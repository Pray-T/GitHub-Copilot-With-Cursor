package com.demo.githubcopilotwithcursor.cursor;

import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.cursor.dto.CursorIdentity;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CursorAuth {

    private static final Logger log = LoggerFactory.getLogger(CursorAuth.class);

    private final CursorProperties properties;
    private final CloudAgentClient cloudAgentClient;
    private volatile CursorIdentity identity;
    private volatile boolean cursorEnabled;

    public CursorAuth(CursorProperties properties, CloudAgentClient cloudAgentClient) {
        this.properties = properties;
        this.cloudAgentClient = cloudAgentClient;
    }

    @PostConstruct
    void initialize() {
        if (!properties.hasApiKey()) {
            cursorEnabled = false;
            log.info("CURSOR_API_KEY is not configured. Cloud Agent features are disabled.");
            return;
        }
        try {
            identity = cloudAgentClient.verifyApiKey();
            cursorEnabled = true;
            log.info("Cursor API key validated for {}. Cloud Agent is enabled.", identity.displayName());
        } catch (RuntimeException exception) {
            cursorEnabled = false;
            log.warn("Cursor API key validation failed: {}", cloudAgentClient.maskSecrets(exception.getMessage()));
        }
    }

    public boolean isCursorEnabled() {
        return cursorEnabled;
    }

    public Optional<CursorIdentity> identity() {
        return Optional.ofNullable(identity);
    }

    public CursorIdentity requireApiKey() {
        if (!cursorEnabled || identity == null) {
            throw new AppException(ErrorCode.CURSOR_AUTH_REQUIRED, "환경변수 CURSOR_API_KEY를 설정한 뒤 앱을 재시작하세요.");
        }
        return identity;
    }
}
