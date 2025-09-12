package com.ai.reviewer.backend.infra.adapter.gitea;

import com.ai.reviewer.backend.domain.adapter.scm.ScmAdapterException;

/**
 * Gitea specific exception for SCM operations.
 */
public class GiteaException extends ScmAdapterException {

    public GiteaException(String operation, String message) {
        super("gitea", operation, message);
    }

    public GiteaException(String operation, String message, Throwable cause) {
        super("gitea", operation, message, cause);
    }

    public GiteaException(String operation, Throwable cause) {
        super("gitea", operation, cause.getMessage(), cause);
    }
}
