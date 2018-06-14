import { S3, SQS } from 'aws-sdk'

const s3 = new S3({
  endpoint: 'http://localhost:5000',
  s3ForcePathStyle: true
})
const sqs = new SQS({
  endpoint: 'http://localhost:5001'
})
