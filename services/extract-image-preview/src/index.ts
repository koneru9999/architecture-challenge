import { SQSLongPolling } from './sqs-long-polling-consumer';
import { SQS, SNS, S3 } from 'aws-sdk';
import { createClient } from 'redis';
import { promisify } from 'util';
import { createWriteStream, unlink, createReadStream, existsSync } from 'fs';

var PDFImage = require('pdf-image').PDFImage;

const sqs = new SQS({ endpoint: 'http://localhost:5001' });
const sns = new SNS({endpoint: 'http://localhost:5002' });
const s3 = new S3({ endpoint: 'http://localhost:5000', s3ForcePathStyle: true });

const QueueName = 'file-stream-images';
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
    //  - each listener can receive up to 1 message
    // With this configuration you could receive and parse 2 messages from SQS.
    // If we need more parallellism, we could increase the concurrency.
    // We could configure this according to our needs.
    const queue = new SQSLongPolling({
      name: QueueName,
      maxNumberOfMessages: 2,
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

      // Once the file is downloaded, we are going to convert PDF to PNG images 
      // and save extracted content as single file back to S3 with `image-parsed/message.Key/pageNum.png` as key
      fileDownloadPromise.then(() => {
        console.log('file downloaded from S3');
        var pdfImage = new PDFImage(tempFile.path);
        pdfImage.numberOfPages().then(function(availablePages) {
          let maxPage = Math.min(maxPagesToProcess, availablePages);
          console.log('No.of pages to process for', message.Key, 'are', maxPage);

          let generateImage = (pageNum: number, pdfImage) => {
            return pdfImage.convertPage(pageNum).then(function (imagePath) {
              const imageFileName = pdfImage.getOutputImagePathForPage(pageNum);
              console.log(imageFileName);

              if(existsSync(imageFileName)) {
                // save the extracted content back to S3
                return s3.putObject({
                  Bucket: message.Bucket,
                  Key: `image-parsed/${message.Key}/${pageNum + 1}.png`,
                  Body: createReadStream(`${imageFileName}`)
                })
                .promise()
                .then(res => {
                  console.info('uploaded thubmail of page', 
                    (pageNum + 1) , 'for', message.Key, 
                    'Extracted content location is images-parsed/',
                    message.Key, '/', (pageNum + 1), '.png');

                    // Clean-up local temp file as well
                    unlink(`${imageFileName}`, (err) => {
                      if (err) {
                        console.info(`unable to remove temp file ${imageFileName} due to`, err);
                      } else {
                        console.info(`Removed temp file ${imageFileName}.png`);
                      }
                    });
                }).catch(err => console.error('Error uploading image file to S3', err));
              } else {
                console.log('image file is not saved for page', pageNum, 'of', message.Key);
              }
            }).catch(err => console.error('Error converting page',pageNum, 'for', message.Key));
          }

          let currPage = 0;
          let lastPromise = generateImage(currPage, pdfImage);
          
          while (currPage <= maxPage) {
            lastPromise = lastPromise.then(generateImage.bind(null, currPage, pdfImage));
            currPage += 1;
          }

          // Once all the content is extracted 
          lastPromise.then(() => {
            e.deleteMessage().then(() => {
              console.info('Deleting processed from Queue', message.Key);
              e.next();
            });
          }); // last promise end

        });
      }).catch(err => console.error(err));
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