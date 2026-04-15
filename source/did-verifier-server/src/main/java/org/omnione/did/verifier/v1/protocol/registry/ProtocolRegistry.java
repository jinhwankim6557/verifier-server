package org.omnione.did.verifier.v1.protocol.registry;

import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.verifier.v1.protocol.handler.ProtocolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProtocolRegistry {

    private final Map<ProtocolType, ProtocolHandler> handlers;

    public ProtocolRegistry(List<ProtocolHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(ProtocolHandler::getProtocolType, Function.identity()));
        log.info("Registered protocol handlers: {}", handlers.keySet());
    }

    public ProtocolHandler getHandler(ProtocolType protocolType) {
        ProtocolHandler handler = handlers.get(protocolType);
        if (handler == null) {
            throw new OpenDidException(ErrorCode.PROTOCOL_HANDLER_NOT_FOUND);
        }
        return handler;
    }
}
