export interface Message {
    type: string;
    data: any;
    message: any;
    url: string;
    name: string;
    workerIndex: number;
    next: Function;
    deleteMessage: () => Promise<any>;
    delay: (timeout: number) => Promise<any>;
    changeMessageVisibility: (timeout: number) => Promise<any>;
}

export type Config = {
    region?: string;
    accessKeyId?: string;
    secretAccessKey?: string;
    visibilityTimeout?: number;
    waitTimeSeconds?: number;
    maxNumberOfMessages?: number;
    name: string;
    concurrency?: number;
    debug?: boolean;
  };