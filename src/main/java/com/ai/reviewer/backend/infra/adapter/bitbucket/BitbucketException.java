package com.ai.reviewer.backend.infra.adapter.bitbucket;

import com.ai.reviewer.backend.domain.adapter.scm.ScmAdapterException;

/**
 * Bitbucket specific exception for SCM operations.
 */
public class BitbucketException extends ScmAdapterException {

    public BitbucketException(String operation, String message) {
        super("bitbucket", operation, message);
    }

    public BitbucketException(String operation, String message, Throwable cause) {
        super("bitbucket", operation, message, cause);
    }

    public BitbucketException(String operation, Throwable cause) {
        super("bitbucket", operation, cause.getMessage(), cause);
    }
}
