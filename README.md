# Caya Backend/Architecture Challenge

Implement a "document processing engine" in the form of microservices for the purpose of processing any type of PDF file that our customers may bring to our platform.

Specifically, we want to implement two microservices that extract the text and create preview images from an incoming document that needs to be processed.

The main goals for this engine are:

- able to scale to process thousands of documents in a short period of time
- simple to extend the pipeline with more steps(ex. today we extract the text and preview images, in the future we may want to extend it such that we train a machine learning model and then run predictions against the model)

### Implementation requirements

- Use Node.js and Typescript to implement the microservices
- (microservice #1) Implement a service to extract text from the PDF file and store it as a file in S3
- (microservice #2) Implement a service to extract preview images (up to the first 10 pages of the document) from the PDF file and store them in S3
- Use the [aws-sdk](https://www.npmjs.com/package/aws-sdk) to make use of the S3 and SQS emulators for file storage and queueing the different tasks

Optional:

- write a few tests(unit/integration) for the implementation
- integrate a monitoring service (either run it via docker or use a cloud service of your choice) and create a few alerts that you deem to be important

### Requirements

- You need to have Docker, node.js and npm installed on your machine
- Some knowledge of AWS S3 and AWS SQS

#### Implementation notes/ideas

There is an `AWS S3` and `AWS SQS` emulator running on ports `5000` and `5001` respectively. This is where you should store all files(PDF itself, text file, preview images) as well as queue the file processing using the `aws-sdk`. You can start the emulator using `docker-compose up -d aws`

There is also a service implemented in `/stream` that will randomly publish messages for files to be processed in SQS. You can start it using `docker-compose up -d stream`. The `QueueName` where it will publish to is `file-stream`. Here is an example of how you can receive messages from this queue:

```ts
const sqs = new SQS({ endpoint: 'http://localhost:5001' })
const { QueueUrl } = await sqs.getQueueUrl({ QueueName: 'file-stream' }).promise()
const { Messages } = await sqs.receiveMessage({ QueueUrl }).promise()
// Process and de-queue Messages
interface MessageBody {
  uid: string // a randomly generated uid
  Bucket: string // the S3 Bucket where the document can be downloaded from
  Key: string // the S3 key for the document PDF file
}
```

### Judging/evaluation notes

We will be looking at the following when evaluating your submission:

- Code style
- Implementation architecture
- Familiarity with the frameworks/libraries/language of your choice

Feel free to use _any_ other library/framework/tool that you find to be useful. Though, the more code you write yourself the better.

### Submission

- Clone the repo
- Implement your solution
- Share your code as a Github/Gitlab/etc repository or a ZIP file
- (optional) Deploy the final solution to a cloud service of your choice
- (optional) Provide detailed instructions on how to start the project locally (if different than running `docker-compose up` to bootstrap all services)

### Questions?

Feel free to reach out to one of us, we are more than happy to assist you with your submission and clarifying any questions you might have related to the task.
