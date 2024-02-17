package com.sismics.books.rest.resource.user_resource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sismics.books.core.constant.Constants;
import com.sismics.books.core.dao.jpa.AuthenticationTokenDao;
import com.sismics.books.core.dao.jpa.RoleBaseFunctionDao;
import com.sismics.books.core.dao.jpa.UserDao;
import com.sismics.books.core.dao.jpa.dto.UserDto;
import com.sismics.books.core.model.jpa.AuthenticationToken;
import com.sismics.books.core.model.jpa.User;
import com.sismics.books.core.util.jpa.PaginatedList;
import com.sismics.books.core.util.jpa.PaginatedLists;
import com.sismics.books.core.util.jpa.SortCriteria;
import com.sismics.books.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.security.UserPrincipal;
import com.sismics.util.filter.TokenBasedSecurityFilter;

@Path("/user/session")
public class UserResource extends BaseResource {

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