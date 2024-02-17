package com.sismics.books.rest.resource.userresource;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sismics.books.core.dao.jpa.AuthenticationTokenDao;
import com.sismics.books.core.model.jpa.AuthenticationToken;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.util.filter.TokenBasedSecurityFilter;

import com.sismics.books.rest.resource.BaseResource;

@Path("/user/session")
public class UserSession extends BaseResource {

    /**
     * Returns all active sessions.
     * 
     * @return Response
     * @throws JSONException
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response session() throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the value of the session token
        String authToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (TokenBasedSecurityFilter.COOKIE_NAME.equals(cookie.getName())) {
                    authToken = cookie.getValue();
                }
            }
        }
        
        JSONObject response = new JSONObject();
        List<JSONObject> sessions = new ArrayList<>();
        
        AuthenticationTokenDao authenticationTokenDao = new AuthenticationTokenDao();

        for (AuthenticationToken authenticationToken : authenticationTokenDao.getByUserId(principal.getId())) {
            JSONObject session = new JSONObject();
            session.put("create_date", authenticationToken.getCreationDate().getTime());
            if (authenticationToken.getLastConnectionDate() != null) {
                session.put("last_connection_date", authenticationToken.getLastConnectionDate().getTime());
            }
            session.put("current", authenticationToken.getId().equals(authToken));
            sessions.add(session);
        }
        response.put("sessions", sessions);
        
        return Response.ok().entity(response).build();
    }
    
    /**
     * Deletes all active sessions except the one used for this request.
     * 
     * @return Response
     * @throws JSONException
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSession() throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the value of the session token
        String authToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (TokenBasedSecurityFilter.COOKIE_NAME.equals(cookie.getName())) {
                    authToken = cookie.getValue();
                }
            }
        }
        
        // Remove other tokens
        AuthenticationTokenDao authenticationTokenDao = new AuthenticationTokenDao();
        authenticationTokenDao.deleteByUserId(principal.getId(), authToken);
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }
}