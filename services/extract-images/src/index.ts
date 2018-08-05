import { SQSLongPolling } from './sqs-long-polling-consumer';

// Simple configuration:
//  - 2 concurrency listeners
//  - each listener can receive up to 4 messages
// With this configuration you could receive and parse 8 `message` events in parallel
const queue = new SQSLongPolling({
  name: 'file-stream-images',
  maxNumberOfMessages: 4,
  concurrency: 2
});

queue.on('message', e => {
  console.log('New message: ', e.data);
  e.deleteMessage().then(() => {
    e.next();
  });
});

queue.on('error', err => {
  console.log('There was an error: ', err);
});