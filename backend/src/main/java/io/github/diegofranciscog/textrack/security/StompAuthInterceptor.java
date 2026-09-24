package io.github.diegofranciscog.textrack.security;

import io.github.diegofranciscog.textrack.config.WebSocketConfig;
import java.util.Set;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Autentica la sesión STOMP con el JWT enviado en el frame CONNECT (los navegadores no permiten cabeceras en el
 * handshake WebSocket) y solo deja suscribirse al tópico del tablero a roles de lectura.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Set<String> DASHBOARD_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_PLANNER", "ROLE_SUPERVISOR", "ROLE_QUALITY", "ROLE_VIEWER");

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter converter;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter converter) {
        this.jwtDecoder = jwtDecoder;
        this.converter = converter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Falta el token de acceso");
            }
            try {
                Jwt jwt = jwtDecoder.decode(header.substring(7));
                AbstractAuthenticationToken authentication = converter.convert(jwt);
                accessor.setUser(authentication);
            } catch (JwtException e) {
                throw new MessageDeliveryException("Token inválido o caducado");
            }
        } else if (command == StompCommand.SUBSCRIBE) {
            if (!(accessor.getUser() instanceof AbstractAuthenticationToken authentication)
                    || authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                            .noneMatch(DASHBOARD_ROLES::contains)) {
                throw new MessageDeliveryException("No autorizado");
            }
            if (!WebSocketConfig.DASHBOARD_TOPIC.equals(accessor.getDestination())) {
                throw new MessageDeliveryException("Destino no permitido");
            }
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("El tablero es de solo lectura");
        }
        return message;
    }
}
