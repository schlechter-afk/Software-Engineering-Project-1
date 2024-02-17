package com.sismics.books.rest.resource.userresource;

import java.util.Date;

import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sismics.books.core.dao.jpa.UserDao;
import com.sismics.books.core.model.jpa.User;
import com.sismics.books.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.security.UserPrincipal;

import com.sismics.books.rest.resource.BaseResource;

@Path("/user")
public class UserHandler extends BaseResource {

    public static final String USERNAME_STRING = "username";
    public static final String EMAIL_STRING = "email";
    public static final String PASSWORD_STRING = "password";
    public static final String STATUS = "status";

    /**
     * Creates a new user.
     * 
     * @param username User's username
     * @param password Password
     * @param email E-Mail
     * @param localeId Locale ID
     * @return Response
     * @throws JSONException
     */
    private static final String DEFAULT_USER_ROLE = "user";
    private static final String DEFAULT_LOCALE_ID = "en";
    private static final String DEFAULT_ADMIN_PASSWORD = "$2a$05$6Ny3TjrW3aVAL1or2SlcR.fhuDgPKp5jp.P9fBXwVNePgeLqb4i3C";
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(
        @FormParam("username") String username,
        @FormParam("password") String password,
        @FormParam("locale") String localeId,
        @FormParam("email") String email) throws JSONException {

        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);
        
        // Validate the input data
        username = ValidationUtil.validateLength(username, USERNAME_STRING, 3, 50);
        ValidationUtil.validateAlphanumeric(username, USERNAME_STRING);
        password = ValidationUtil.validateLength(password, PASSWORD_STRING, 8, 50);
        email = ValidationUtil.validateLength(email, EMAIL_STRING, 3, 50);
        ValidationUtil.validateEmail(email, EMAIL_STRING);
        
        // Create the user
        User user = new User();
        user.setRoleId(DEFAULT_USER_ROLE);
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setCreateDate(new Date());
        user.setLocaleId(DEFAULT_LOCALE_ID);
        
        // Create the user
        UserDao userDao = new UserDao();
        try {
            userDao.create(user);
        } catch (Exception e) {
            if ("AlreadyExistingUsername".equals(e.getMessage())) {
                throw new ServerException("AlreadyExistingUsername", "Login already used", e);
            } else {
                throw new ServerException("UnknownError", "Unknown Server Error", e);
            }
        }
        
        // Always return OK
        JSONObject response = new JSONObject();
        response.put(STATUS, "ok");
        return Response.ok().entity(response).build();
    }

    /**
     * Updates user informations.
     * 
     * @param password Password
     * @param email E-Mail
     * @param themeId Theme
     * @param localeId Locale ID
     * @param firstConnection True if the user hasn't acknowledged the first connection wizard yet.
     * @return Response
     * @throws JSONException
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(
        @FormParam("password") String password,
        @FormParam("email") String email,
        @FormParam("theme") String themeId,
        @FormParam("locale") String localeId,
        @FormParam("first_connection") Boolean firstConnection) throws JSONException {
        
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate the input data
        password = ValidationUtil.validateLength(password, PASSWORD_STRING, 8, 50, true);
        email = ValidationUtil.validateLength(email, EMAIL_STRING, null, 100, true);
        localeId = ValidationUtil.validateLocale(localeId, "locale", true);
        themeId = ValidationUtil.validateTheme(themeId, "theme", true);
        
        // Update the user
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(principal.getName());
        if (email != null) {
            user.setEmail(email);
        }
        if (themeId != null) {
            user.setTheme(themeId);
        }
        if (localeId != null) {
            user.setLocaleId(localeId);
        }
        if (firstConnection != null && hasBaseFunction(BaseFunction.ADMIN)) {
            user.setFirstConnection(firstConnection);
        }
        
        user = userDao.update(user);
        
        if (StringUtils.isNotBlank(password)) {
            user.setPassword(password);
            userDao.updatePassword(user);
        }
        
        // Always return "ok"
        JSONObject response = new JSONObject();
        response.put(STATUS, "ok");
        return Response.ok().entity(response).build();
    }

    /**
     * Delete a user.
     * 
     * @return Response
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete() throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Ensure that the admin user is not deleted
        if (hasBaseFunction(BaseFunction.ADMIN)) {
            throw new ClientException("ForbiddenError", "The admin user cannot be deleted");
        }
        
        // Delete the user
        UserDao userDao = new UserDao();
        userDao.delete(principal.getName());
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put(STATUS, "ok");
        return Response.ok().entity(response).build();
    }
    
    /**
     * Returns the information about the connected user.
     * 
     * @return Response
     * @throws JSONException
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response info() throws JSONException {
        JSONObject response = new JSONObject();
        if (!authenticate()) {
            response.put("anonymous", true);

            // Check if admin has the default password
            UserDao userDao = new UserDao();
            User adminUser = userDao.getById("admin");
            if (adminUser != null && adminUser.getDeleteDate() == null) {
                response.put("is_default_password", DEFAULT_ADMIN_PASSWORD.equals(adminUser.getPassword()));
            }
        } else {
            response.put("anonymous", false);
            UserDao userDao = new UserDao();
            User user = userDao.getById(principal.getId());
            response.put(USERNAME_STRING, user.getUsername());
            response.put(EMAIL_STRING, user.getEmail());
            response.put("theme", user.getTheme());
            response.put("locale", user.getLocaleId());
            response.put("first_connection", user.isFirstConnection());
            JSONArray baseFunctions = new JSONArray(((UserPrincipal) principal).getBaseFunctionSet());
            response.put("base_functions", baseFunctions);
            response.put("is_default_password", hasBaseFunction(BaseFunction.ADMIN) && DEFAULT_ADMIN_PASSWORD.equals(user.getPassword()));
        }
        
        return Response.ok().entity(response).build();
    }
}