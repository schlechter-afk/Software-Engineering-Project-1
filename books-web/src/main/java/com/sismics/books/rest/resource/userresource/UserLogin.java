package com.sismics.books.rest.resource.userresource;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sismics.books.core.dao.jpa.AuthenticationTokenDao;
import com.sismics.books.core.dao.jpa.UserDao;
import com.sismics.books.core.model.jpa.AuthenticationToken;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.util.filter.TokenBasedSecurityFilter;

import com.sismics.books.rest.resource.BaseResource;

@Path("/user/login")
public class UserLogin extends BaseResource {

    /**
     * This resource is used to authenticate the user and create a user ession.
     * The "session" is only used to identify the user, no other data is stored in the session.
     * 
     * @param username Username
     * @param password Password
     * @param longLasted Remember the user next time, create a long lasted session.
     * @return Response
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(
        @FormParam("username") String username,
        @FormParam("password") String password,
        @FormParam("remember") boolean longLasted) throws JSONException {
        
        // Validate the input data
        username = StringUtils.strip(username);
        password = StringUtils.strip(password);

        // Get the user
        UserDao userDao = new UserDao();
        String userId = userDao.authenticate(username, password);
        if (userId == null) {
            throw new ForbiddenClientException();
        }
            
        // Create a new session token
        AuthenticationTokenDao authenticationTokenDao = new AuthenticationTokenDao();
        AuthenticationToken authenticationToken = new AuthenticationToken();
        authenticationToken.setUserId(userId);
        authenticationToken.setLongLasted(longLasted);
        String token = authenticationTokenDao.create(authenticationToken);
        
        // Cleanup old session tokens
        authenticationTokenDao.deleteOldSessionToken(userId);

        JSONObject response = new JSONObject();
        int maxAge = longLasted ? TokenBasedSecurityFilter.TOKEN_LONG_LIFETIME : -1;
        NewCookie cookie = new NewCookie(TokenBasedSecurityFilter.COOKIE_NAME, token, "/", null, null, maxAge, false);
        return Response.ok().entity(response).cookie(cookie).build();
    }
}
