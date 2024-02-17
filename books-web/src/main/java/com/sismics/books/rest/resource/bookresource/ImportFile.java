package com.sismics.books.rest.resource.bookresource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
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
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.sismics.books.core.dao.jpa.BookDao;
import com.sismics.books.core.dao.jpa.TagDao;
import com.sismics.books.core.dao.jpa.UserBookDao;
import com.sismics.books.core.dao.jpa.UserDao;
import com.sismics.books.core.dao.jpa.criteria.UserBookCriteria;
import com.sismics.books.core.dao.jpa.dto.TagDto;
import com.sismics.books.core.dao.jpa.dto.UserBookDto;
import com.sismics.books.core.event.BookImportedEvent;
import com.sismics.books.core.model.context.AppContext;
import com.sismics.books.core.model.jpa.Book;
import com.sismics.books.core.model.jpa.Tag;
import com.sismics.books.core.model.jpa.User;
import com.sismics.books.core.model.jpa.UserBook;
import com.sismics.books.core.util.DirectoryUtil;
import com.sismics.books.core.util.jpa.PaginatedList;
import com.sismics.books.core.util.jpa.PaginatedLists;
import com.sismics.books.core.util.jpa.SortCriteria;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.ValidationUtil;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataParam;

import com.sismics.books.rest.resource.BaseResource;

@Path("/book/import")
public class ImportFile extends BaseResource {
    
    /**
     * Imports books.
     * 
     * @param fileBodyPart File to import
     * @return Response
     * @throws JSONException
     */
    @PUT
    @Consumes("multipart/form-data")
    public Response importFile(
            @FormDataParam("file") FormDataBodyPart fileBodyPart) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        ValidationUtil.validateRequired(fileBodyPart, "file");

        UserDao userDao = new UserDao();
        User user = userDao.getById(principal.getId());
        
        InputStream in = fileBodyPart.getValueAs(InputStream.class);
        File importFile = null;
        try {
            // Copy the incoming stream content into a temporary file
            importFile = File.createTempFile("books_import", null);
            IOUtils.copy(in, new FileOutputStream(importFile));
            
            BookImportedEvent event = new BookImportedEvent();
            event.setUser(user);
            event.setImportFile(importFile);
            AppContext.getInstance().getImportEventBus().post(event);
            
            // Always return ok
            JSONObject response = new JSONObject();
            response.put("status", "ok");
            return Response.ok().entity(response).build();
        } catch (Exception e) {
            if (importFile != null) {
                try {
                    importFile.delete();
                } catch (SecurityException e2) {
                    // NOP
                }
            }
            throw new ServerException("ImportError", "Error importing books", e);
        }
    }    
}