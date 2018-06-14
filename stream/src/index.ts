import { S3, SQS } from 'aws-sdk'
import { v4 } from 'uuid'
import { createReadStream } from 'fs'
import { sample, random } from 'lodash'
import { join } from 'path'

const s3 = new S3({ endpoint: 'http://localhost:5000', s3ForcePathStyle: true })
const sqs = new SQS({ endpoint: 'http://localhost:5001' })

const uploadFile = async (filePath: string, Bucket: string, Key: string = filePath) => {
  return s3
    .putObject({
      Bucket,
      Key,
      Body: createReadStream(filePath),
      ContentType: 'image/png'
    })
    .promise()
}

const createDocument = async (QueueUrl: string, Bucket: string) => {
  const samples = ['drylab.pdf', 'example.pdf', 'flyer.pdf', 'magic.pdf', 'PrinceCatalogue.pdf']
  const filePath = join(__dirname, `documents/${sample(samples)}`)
  const uid = v4()
  const Key = `${uid}.pdf`
  await uploadFile(filePath, Bucket, Key)
  await sqs.sendMessage({ QueueUrl, MessageBody: JSON.stringify({ uid, Bucket, Key }) }).promise()
}

async function initialize() {
  const QueueName = 'file-stream'
  const Bucket = 'file-stream'
  const { QueueUrl } = await sqs.createQueue({ QueueName }).promise()
  await s3.createBucket({ Bucket }).promise()
  return { QueueUrl, Bucket }
}

async function generate(options) {
  const { Bucket, QueueUrl } = options
  const timeout = random(1000)
  const interval = setTimeout(async () => {
    await createDocument(QueueUrl, Bucket)
    await createDocument(QueueUrl, Bucket)
    await createDocument(QueueUrl, Bucket)
    console.log('Created 3 documents after ', timeout)
    clearTimeout(interval)
    generate(options)
  }, timeout)
}

initialize().then(generate)
