import { SQSLongPolling } from './sqs-long-polling-consumer';
import { SQS, SNS, S3 } from 'aws-sdk';
import { createClient } from 'redis';
import { promisify, inherits } from 'util';
import { createWriteStream, unlink, readFileSync, createReadStream } from 'fs';
import { Readable } from 'stream';

let pdfjs = require("pdfjs-dist");

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
    //  - 2 concurrency listeners
    //  - each listener can receive up to 4 messages
    // With this configuration you could receive and parse 8 messages from SQS in parallel
    // We could configure this according to our needs.
    const queue = new SQSLongPolling({
      name: QueueName,
      maxNumberOfMessages: 4,
      concurrency: 2,
      sqs
    });

    queue.on('message', e => {
      let message = JSON.parse(e.data.Message);
      console.info('Message received from SQS', message);

      // Create a temp file to save file from S3
      let tempFile = createWriteStream(message.Key);

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

        pdfjs.getDocument({
          data: new Uint8Array(readFileSync(message.Key))
        })
          .then(doc => {
            let maxPage = Math.min(maxPagesToProcess, doc.numPages);

            console.info('Total number of pages for', message.Key, 'are', doc.numPages);
            if (maxPage !== doc.numPages) {
              console.info('We only processing', maxPagesToProcess, 'at the moment as per the configuration');
            }

            var lastPromise = Promise.resolve();
            var loadPage = function (pageNum) {
              return doc.getPage(pageNum).then(function (page) {
                console.log('# Page ' + pageNum);
                var viewport = page.getViewport(1.0 /* scale */);
                
                return page.getOperatorList().then(function (opList) {
                  var svgGfx = new pdfjs.SVGGraphics(page.commonObjs, page.objs);
                  svgGfx.embedFonts = true;
                  return svgGfx.getSVG(opList, viewport).then(function (svg) {
                    return writeSvgToFile(svg, message.Key + '-page-' + pageNum + '.svg').then(function () {
                      console.log('# Page', pageNum);


                      // save the extracted content back to S3
                      s3.putObject({
                        Bucket: message.Bucket,
                        Key: `image-parsed/${message.Key}/page-${pageNum}.svg`,
                        Body: createReadStream(message.Key + '-page-' + pageNum + '.svg')
                      })
                      .promise()
                      .then(res => {
                        console.info('uploaded extracted content for', message.Key, 'Extracted content location is images-extracted/' + message.Key + '/page-' + pageNum + '.svg');

                        e.deleteMessage().then(() => {
                          console.info('Deleting processed from Queue', message.Key);
                          e.next();
                        });
                        
                        // Clean-up local temp file as well
                        unlink(message.Key + '-page-' + pageNum + '.svg', (err) => {
                          if (err) {
                            console.info('unable to remove temp file', message.Key + '-page-' + pageNum + '.svg', 'due to', err);
                          } else {
                            console.info('Removed temp file', message.Key + '-page-' + pageNum + '.svg');
                          }
                        });

                      }); // end of putObject

                    }, function(err) {
                      console.log('Error: ', err);
                    });
                  });
                });
              });
            };

            let currPage = 1;
            while (currPage <= maxPage) {
              lastPromise = lastPromise.then(loadPage.bind(null, currPage));
              currPage++;
            }

            return lastPromise;
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

/**
 * A readable stream which offers a stream representing the serialization of a
 * given DOM element (as defined by domstubs.js).
 *
 * @param {object} options
 * @param {DOMElement} options.svgElement The element to serialize
 */
function ReadableSVGStream(options): void {
  if (!(this instanceof ReadableSVGStream)) {
    return new ReadableSVGStream(options);
  }
  
  Readable.call(this, options);
  
  this.serializer = options.svgElement.getSerializer();
}

inherits(ReadableSVGStream, Readable);

// Implements https://nodejs.org/api/stream.html#stream_readable_read_size_1
ReadableSVGStream.prototype._read = function() {
  var chunk;
  while ((chunk = this.serializer.getNext()) !== null) {
    if (!this.push(chunk)) {
      return;
    }
  }
  this.push(null);
};

// Streams the SVG element to the given file path.
function writeSvgToFile(svgElement, filePath) {
  var readableSvgStream = new ReadableSVGStream({
    svgElement: svgElement,
  });

  var writableStream = createWriteStream(filePath);
  
  return new Promise(function(resolve, reject) {
    readableSvgStream.once('error', reject);
    writableStream.once('error', reject);
    writableStream.once('finish', resolve);
    readableSvgStream.pipe(writableStream);
  }).catch(function(err) {
    readableSvgStream = null; // Explicitly null because of v8 bug 6512.
    writableStream.end();
    throw err;
  });
}