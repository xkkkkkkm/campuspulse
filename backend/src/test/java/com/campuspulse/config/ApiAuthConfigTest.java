package com.campuspulse.config;
import com.campuspulse.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ApiAuthConfigTest {
    @AfterEach void clear() { AuthContext.clear(); }
    @Test void publicAllowlistDoesNotExposeNestedPrivateOrFutureRoutes() throws Exception {
        var interceptor=new ApiAuthConfig.ApiAuthInterceptor(mock(TokenService.class),new ObjectMapper(),new RequestRateLimiter());
        for(String path:new String[]{"/api/activities/7/registrations","/api/activities/7/chat/messages","/api/teams/1/requests","/api/support/tickets","/api/tags/new-private-route"}) {
            var response=new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET",path),response,new Object()));
            assertEquals(401,response.getStatus());
        }
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET","/api/activities/7"),new MockHttpServletResponse(),new Object()));
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST","/api/activities/7"),new MockHttpServletResponse(),new Object()));
    }
    @Test void anonymousTeamPaginationIsAllowedWithoutOpeningSiblingPathsOrWrites() throws Exception {
        var interceptor=new ApiAuthConfig.ApiAuthInterceptor(mock(TokenService.class),new ObjectMapper(),new RequestRateLimiter());
        var request=new MockHttpServletRequest("GET","/api/teams/page");
        request.setQueryString("page=2&pageSize=20");
        assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        assertNull(AuthContext.userOrNull());
        for(String path:new String[]{"/api/teams/page/private","/api/teams/pages","/api/teams/page-export"}) {
            var response=new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET",path),response,new Object()));
            assertEquals(401,response.getStatus());
        }
        var writeResponse=new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST","/api/teams/page"),writeResponse,new Object()));
        assertEquals(401,writeResponse.getStatus());
    }

}
