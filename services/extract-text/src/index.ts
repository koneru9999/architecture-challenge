import { SQSLongPolling } from './sqs-long-polling-consumer';
import { SQS, SNS, S3 } from 'aws-sdk';
import { createClient } from 'redis';
import { promisify } from 'util';
import { createWriteStream, unlink } from 'fs';

let pdfjs = require("pdfjs-dist");

const sqs = new SQS({ endpoint: 'http://localhost:5001' });
const sns = new SNS({endpoint: 'http://localhost:5002' });
const s3 = new S3({ endpoint: 'http://localhost:5000', s3ForcePathStyle: true });

const QueueName = 'file-stream-text';
const redisKey = 'topicArn';

const redisPort = process.env.REDIS_PORT ? +process.env.REDIS_PORT : 6379;
const maxPagesToProcess = process.env.MAX_EXTRACT_PAGES ? +process.env.MAX_EXTRACT_PAGES : 10;

async function initialize() {
  const { QueueUrl } = await sqs.createQueue({ QueueName }).promise()

  // Get Queue ARN
  const { Attributes } = await sqs.getQueueAttributes({QueueUrl, AttributeNames: ["QueueArn"]}).promise()

  // Redis Client
  const redisClient = createClient(redisPort, process.env.REDIS_HOST || 'localhost');
  const getAsync = promisify(redisClient.get).bind(redisClient);

  // Get Topic ARN -- ideally this is acheived using AWS Console
  // Simple retry logic to wait until the topic ARN from SNS is retrieved
  let isSuccess = false;
  while (!isSuccess) {
    const topicArn = await getAsync(redisKey);
    if (topicArn) {
      console.log('Topic received from redis', topicArn);
      isSuccess = true;
      redisClient.quit(); // close the connection as we don't need it anymore

      // Subscribe to SNS topic
      await sns.subscribe({
            Protocol: 'sqs',
            TopicArn: topicArn,
            Endpoint: Attributes.QueueArn
          }).promise();

    }
  }
}

initialize().then(
  () => {
    // Simple configuration:
    //  - 1 concurrency listeners
    //  - each listener can receive up to 4 messages
    // With this configuration you could receive and parse 4 messages from SQS
    // If we need more parallellism, we could increase the concurrency.
    // We could configure this according to our needs.
    const queue = new SQSLongPolling({
      name: QueueName,
      maxNumberOfMessages: 4,
      concurrency: 1,
      sqs
    });

    queue.on('message', e => {
      let message = JSON.parse(e.data.Message);
      console.info('Message received from SQS', message);

      // Create a temp file to save file from S3
      let tempFile = createWriteStream(`/tmp/${message.Key}`);

      // Download file from S3
      let fileDownloadPromise = new Promise((resolve, reject) => {
        
        s3.getObject({
          Bucket: message.Bucket, // Bucket info from the message can be used
          Key: message.Key
        }).createReadStream()
          .on('end', () => {
              return resolve();
          })
          .on('error', (error) => {
              return reject(error);
          }).pipe(tempFile);
      });

      // Once the file is downloaded, we are going to extract the text 
      // and save extracted content as single file back to S3 with `text-parsed/message.Key` as key
      fileDownloadPromise.then(() => {

        pdfjs.getDocument(`/tmp/${message.Key}`)
          .then(doc => {
            let maxPage = Math.min(maxPagesToProcess, doc.numPages);

            console.info('Total number of pages for', message.Key, 'are', doc.numPages);
            if (maxPage !== doc.numPages) {
              console.info('We will only process', maxPagesToProcess, 'at the moment as per the configuration');
            }

            let textContent = [];
            let currPage = 1; // start with first page

            let extractText = (pageNum: number, arrToPush: string[], fileName: string) => {
              console.log('# Page', pageNum, fileName);
              return doc.getPage(pageNum).then(page => {
                return page.getTextContent().then(content => {
                  // Content contains lots of information about the text layout and
                  // styles, but we need only strings at the moment
                  let strings = content.items.map(item => {
                    return item.str;
                  });
                  console.debug('# Content for page', pageNum, 'of', fileName, strings.join(' '));
                  arrToPush.push(strings.join(' '));
                });
              });
            };

            let lastPromise = extractText(currPage, textContent, message.Key);
            currPage += 1;

            while (currPage <= maxPage) {
              lastPromise = lastPromise.then(extractText.bind(null, currPage, textContent, message.Key));
              currPage += 1;
            }

            // Once all the content is extracted 
            lastPromise.then(() => {
              // convert to buffer
              var buf = Buffer.from(textContent.join(''), 'utf8');

              // save the extracted content back to S3
              s3.putObject({
                Bucket: message.Bucket,
                Key: `text-parsed/${message.Key}`,
                Body: buf
              })
              .promise()
              .then(res => {
                console.info('uploaded extracted content for', message.Key, 'Extracted content location is text-parsed/' + message.Key);

                e.deleteMessage().then(() => {
                  console.info('Deleting processed from Queue', message.Key);
                  e.next();
                });
                
                // Clean-up local temp file as well
                unlink(`/tmp/${message.Key}`, (err) => {
                  if (err) {
                    console.info('unable to remove temp file /tmp/', message.Key, 'due to', err);
                  } else {
                    console.info('Removed temp file /tmp/', message.Key);
                  }
                });

              }); // end of putObject

            }); // end of lastPromise then

          }); // end of getDocument
      });
    });

    queue.on('error', err => {
      console.log('There was an error: ', err);
    });
  }
);

function tou8(buffer: Buffer) {
  let a = new Uint8Array(buffer.length);
    for (var i = 0; i < buffer.length; i++) a[i] = buffer[i];
  return a;
}