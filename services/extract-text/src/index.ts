import { SQSLongPolling } from './sqs-long-polling-consumer';
import { SQS, SNS, S3 } from 'aws-sdk';
import { createClient } from 'redis';
import { PDFJSStatic } from 'pdfjs-dist';
import { promisify } from 'util';

let pdfjs: PDFJSStatic = require("pdfjs-dist");

async function initialize() {
  const QueueName = 'file-stream-text';
  const redisKey = 'topicArn';

  let sqs = new SQS({ endpoint: 'http://localhost:5001' });
  const { QueueUrl } = await sqs.createQueue({ QueueName }).promise()

  // Get Queue ARN
  const { Attributes } = await sqs.getQueueAttributes({QueueUrl, AttributeNames: ["QueueArn"]}).promise()

  // Get Topic ARN -- ideally this is acheived using AWS Console
  const redisPort = process.env.REDIS_PORT ? +process.env.REDIS_PORT : 6379;
  const redisClient = createClient(redisPort, process.env.REDIS_HOST || 'localhost');
  const getAsync = promisify(redisClient.get).bind(redisClient);

  // Simple retry logic to wait until the topic ARN is retrieved
  let isSuccess = false;
  while (!isSuccess) {
    const topicArn = await getAsync(redisKey);
    console.log('Topic from redis: ', topicArn);
    if (topicArn) {
      isSuccess = true;
      await new SNS({endpoint: 'http://localhost:5002' }).subscribe({
            Protocol: 'sqs',
            TopicArn: topicArn,
            Endpoint: Attributes.QueueArn
          }).promise();
    }
  }
}

initialize().then(
  () => {
    const s3 = new S3({ endpoint: 'http://localhost:5000', s3ForcePathStyle: true })
    // Simple configuration:
    //  - 2 concurrency listeners
    //  - each listener can receive up to 4 messages
    // With this configuration you could receive and parse 8 `message` events in parallel
    const queue = new SQSLongPolling({
      name: 'file-stream-text',
      maxNumberOfMessages: 4,
      concurrency: 2
    });

    queue.on('message', e => {
      let message = JSON.parse(e.data.Message);

      console.info(`file key ${message.Key}`);

      // read file from S3
      s3.getObject({Bucket: message.Bucket, Key: message.Key}, (err, data) => {
        if (err) {
          console.error('Error downloading documnet from S3 ', err);
          return;
        }
        console.info('completed downloading file frm S3', message.Key);

        pdfjs.getDocument(new Uint8Array(<Buffer>data.Body)) 
          .then(doc => {
            let maxPage = Math.min(10, doc.numPages);

            let textContent = [];
            let currPage = 1;

            var extractText = function (pageNum, arrToPush) {
              return doc.getPage(pageNum).then(function (page) {
                console.log('# Page ' + pageNum);
                return page.getTextContent().then(function (content) {
                  // Content contains lots of information about the text layout and
                  // styles, but we need only strings at the moment
                  var strings = content.items.map(function (item) {
                    return item.str;
                  });
                  arrToPush.push(strings.join(' '));
                });
              });
            };

            let lastPromise = extractText(1, textContent);
            currPage ++;

            while (currPage <= maxPage) {
              lastPromise = lastPromise.then(extractText.bind(currPage, textContent));
              currPage++;
            }

            lastPromise.then(() => {
              // convert to buffer
              var buf = Buffer.from(textContent.join(''), 'utf8');

              // save the extracted content back to S3
              s3.putObject({
                Bucket: 'file-stream',
                Key: `text-parsed/${message.Key}`,
                Body: buf
              })
              .promise()
              .then(res => {
                console.info(`uploaded extracted content for ${message.Key}. Extracted content location is text-parsed/${message.Key}`);

                e.deleteMessage().then(() => {
                  e.next();
                });
              }); // end of putObject

            }); // end of lastPromise then

          }); // end of getDocument

      }); // end of get object    
    });

    queue.on('error', err => {
      console.log('There was an error: ', err);
    });
  }
);