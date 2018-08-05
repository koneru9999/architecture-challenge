/**
 * 
 */
export interface RetryStartegy {
    /**
     * returns the delay amount in millis
     */
    getDelay(retryCount: number): number;

    /**
     * to reset the delay after max tries. Default is true.
     */
    setRestAfterMaxTries(resetAfterMaxTries: boolean);
}

export class LinearRetryStrategy implements RetryStartegy {
    resetAfterMaxTries = true;
    maxTries = 10;

    getDelay(retryCount: number): number {
        let _retryCount = retryCount;

        if (this.resetAfterMaxTries) {
            _retryCount = _retryCount > this.maxTries ? (_retryCount % this.maxTries) : _retryCount;
        }

        return _retryCount * 1000;
    }

    setRestAfterMaxTries(resetAfterMaxTries: boolean): void {
        this.resetAfterMaxTries = resetAfterMaxTries;
    }
}

/**
 * Retry strategy to have delays based on the sum of the Fibonacci series of the retry counter
 */
export class FibonacciRetryStrategy implements RetryStartegy {
    resetAfterMaxTries = true;
    maxTries = 10;

    getDelay(retryCount: number): number {
        let _retryCount = retryCount;

        if (this.resetAfterMaxTries) {
            _retryCount = _retryCount > this.maxTries ? (_retryCount % this.maxTries) : _retryCount;
        }

        return this.fibonacci(_retryCount) * 1000;
    }

    setRestAfterMaxTries(resetAfterMaxTries: boolean): void {
        this.resetAfterMaxTries = resetAfterMaxTries;
    }
    
    private fibonacci(num): number {
        let a = 1, b = 0, temp;
      
        while (num >= 0){
          temp = a;
          a = a + b;
          b = temp;
          num--;
        }
      
        return b;
    }

}

/**
 * Retry strategy to have delays based on exponential curve.
 */
export class ExponentialRetryStrategy implements RetryStartegy {
    resetAfterMaxTries = true;
    maxTries = 10;

    getDelay(retryCount: number): number {
        let _retryCount = retryCount;

        if (this.resetAfterMaxTries) {
            _retryCount = _retryCount > this.maxTries ? (_retryCount % this.maxTries) : _retryCount;
        }
        return 2 * _retryCount * 2000;
    }

    setRestAfterMaxTries(resetAfterMaxTries: boolean): void {
        this.resetAfterMaxTries = resetAfterMaxTries;
    }
}

export enum RetryStrategies {
    LINEAR, FIBONACCI, EXPONENTIAL
}

export class RetryStrategyFactory {
    static getRetryStrategy(strategy: RetryStrategies) {
        switch(strategy) {
            case RetryStrategies.FIBONACCI: 
                return new FibonacciRetryStrategy();
            case RetryStrategies.EXPONENTIAL:
                return new ExponentialRetryStrategy();
            case RetryStrategies.LINEAR:
            default: 
                return new LinearRetryStrategy();
        }
    }
}