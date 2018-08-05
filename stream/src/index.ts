import { S3, SNS } from 'aws-sdk'
import { v4 } from 'uuid'
import { createReadStream } from 'fs'
import { sample, random } from 'lodash'
import { join } from 'path'
import { createClient } from 'redis';

const s3 = new S3({ endpoint: 'http://localhost:5000', s3ForcePathStyle: true })
const sns = new SNS({ endpoint: 'http://localhost:5002'})

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

const createDocument = async (TopicArn: string, Bucket: string) => {
  const samples = ['drylab.pdf', 'example.pdf', 'flyer.pdf', 'magic.pdf', 'PrinceCatalogue.pdf']
  const filePath = join(__dirname, `documents/${sample(samples)}`)
  const uid = v4()
  const Key = `${uid}.pdf`
  await uploadFile(filePath, Bucket, Key)
  await sns.publish({TopicArn, Message:  JSON.stringify({ uid, Bucket, Key }) }).promise()
}

async function initialize() {
  const Bucket = 'file-stream'
  const Name = 'file-stream'
  
  const { TopicArn } = await sns.createTopic({Name}).promise();

  // Save the topic ARN to redis so that other servcices can fetch in-order to subscribe to topic
  const redis = await createClient(+process.env.REDIS_PORT || 6379, process.env.REDIS_HOST || 'localhost');
  redis.set('topicArn', TopicArn);

  await s3.createBucket({ Bucket }).promise()
  return { TopicArn, Bucket }
}

async function generate(options) {
  const { Bucket, TopicArn } = options
  const timeout = random(1000)
  const interval = setTimeout(async () => {
    await createDocument(TopicArn, Bucket)
    await createDocument(TopicArn, Bucket)
    await createDocument(TopicArn, Bucket)
    console.log('Created 3 documents after ', timeout)
    clearTimeout(interval)
    generate(options)
  }, 10000)
}

initialize().then(generate)
