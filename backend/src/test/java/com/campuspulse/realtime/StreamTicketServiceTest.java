package com.campuspulse.realtime;
import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import com.campuspulse.security.TokenService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StreamTicketServiceTest {
    @Test void ticketsAreSingleUseBoundedAndRevocable() {
        TokenService tokens = mock(TokenService.class);
        when(tokens.isCurrent(any())).thenReturn(true);
        StreamTicketService service = new StreamTicketService(tokens);
        AuthContext.User user = new AuthContext.User(1,"alice","USER");
        String ticket = service.issue(user);
        assertTrue(service.consume(ticket).isPresent());
        assertTrue(service.consume(ticket).isEmpty());
        String revoked = service.issue(user);
        when(tokens.isCurrent(any())).thenReturn(false);
        assertTrue(service.consume(revoked).isEmpty());
        for (int i=0;i<6;i++) service.issue(user);
        assertThrows(Api.ApiException.class, () -> service.issue(user));
    }
}
