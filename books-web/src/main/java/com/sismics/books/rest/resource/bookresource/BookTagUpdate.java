package com.sismics.books.rest.resource.bookresource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.google.common.base.Strings;
import com.sismics.books.core.dao.jpa.BookDao;
import com.sismics.books.core.dao.jpa.TagDao;
import com.sismics.books.core.dao.jpa.UserBookDao;
import com.sismics.books.core.dao.jpa.dto.TagDto;
import com.sismics.books.core.model.context.AppContext;
import com.sismics.books.core.model.jpa.Book;
import com.sismics.books.core.model.jpa.Tag;
import com.sismics.books.core.model.jpa.UserBook;
import com.sismics.books.core.util.DirectoryUtil;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.books.rest.resource.BaseResource;

@Path("/book/{id: [a-z0-9\\-]+}")
public class BookTagUpdate extends BaseResource{

    public static final String BOOKNOTFOUND = "BookNotFound";
    public static final String BOOKNOTFOUNDMESSAGE = "Book not found with id";
    public static final String STATUS = "status";

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(
            @PathParam("id") String userBookId) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the user book
        UserBookDao userBookDao = new UserBookDao();
        UserBook userBook = userBookDao.getUserBook(userBookId, principal.getId());
        if (userBook == null) {
            throw new ClientException(BOOKNOTFOUND, BOOKNOTFOUNDMESSAGE + userBookId);
        }
        
        // Delete the user book
        userBookDao.delete(userBook.getId());
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put(STATUS, "ok");
        return Response.ok().entity(response).build();
    }


  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public Response update(
          @PathParam("id") String userBookId,
          @FormParam("title") String title,
          @FormParam("subtitle") String subtitle,
          @FormParam("author") String author,
          @FormParam("description") String description,
          @FormParam("isbn10") String isbn10,
          @FormParam("isbn13") String isbn13,
          @FormParam("page_count") Long pageCount,
          @FormParam("language") String language,
          @FormParam("publish_date") String publishDateStr,
          @FormParam("tags") List<String> tagList) throws JSONException {
      if (!authenticate()) {
          throw new ForbiddenClientException();
      }
      
      // Validate input data
      title = ValidationUtil.validateLength(title, "title", 1, 255, true);
      subtitle = ValidationUtil.validateLength(subtitle, "subtitle", 1, 255, true);
      author = ValidationUtil.validateLength(author, "author", 1, 255, true);
      description = ValidationUtil.validateLength(description, "description", 1, 4000, true);
      isbn10 = ValidationUtil.validateLength(isbn10, "isbn10", 10, 10, true);
      isbn13 = ValidationUtil.validateLength(isbn13, "isbn13", 13, 13, true);
      language = ValidationUtil.validateLength(language, "language", 2, 2, true);
      Date publishDate = ValidationUtil.validateDate(publishDateStr, "publish_date", true);
      
      

      // Get the user book
      UserBookDao userBookDao = new UserBookDao();
      BookDao bookDao = new BookDao();
      UserBook userBook = userBookDao.getUserBook(userBookId, principal.getId());
      if (userBook == null) {
          throw new ClientException(BOOKNOTFOUND, BOOKNOTFOUNDMESSAGE + userBookId);
      }
      
      // Get the book
      Book book = bookDao.getById(userBook.getBookId());
      
      // Check that new ISBN number are not already in database
      if (!Strings.isNullOrEmpty(isbn10) && book.getIsbn10() != null && !book.getIsbn10().equals(isbn10)) {
          Book bookIsbn10 = bookDao.getByIsbn(isbn10);
          if (bookIsbn10 != null) {
              throw new ClientException("BookAlreadyAdded", "Book already added");
          }
      }
      
      if (!Strings.isNullOrEmpty(isbn13) && book.getIsbn13() != null && !book.getIsbn13().equals(isbn13)) {
          Book bookIsbn13 = bookDao.getByIsbn(isbn13);
          if (bookIsbn13 != null) {
              throw new ClientException("BookAlreadyAdded", "Book already added");
          }
      }
      
      // Update the book
    if (title != null) {
        book.setTitle(title);
    }
    if (subtitle != null) {
        book.setSubtitle(subtitle);
    }
    if (author != null) {
        book.setAuthor(author);
    }
    if (description != null) {
        book.setDescription(description);
    }
    if (isbn10 != null) {
        book.setIsbn10(isbn10);
    }
    if (isbn13 != null) {
        book.setIsbn13(isbn13);
    }
    if (pageCount != null) {
        book.setPageCount(pageCount);
    }
    if (language != null) {
        book.setLanguage(language);
    }
    if (publishDate != null) {
        book.setPublishDate(publishDate);
    }
      
      // Update tags
      if (tagList != null) {
          TagDao tagDao = new TagDao();
          Set<String> tagSet = new HashSet<>();
          Set<String> tagIdSet = new HashSet<>();
          List<Tag> tagDbList = tagDao.getByUserId(principal.getId());
          for (Tag tagDb : tagDbList) {
              tagIdSet.add(tagDb.getId());
          }
          for (String tagId : tagList) {
              if (!tagIdSet.contains(tagId)) {
                  throw new ClientException("TagNotFound", MessageFormat.format("Tag not found: {0}", tagId));
              }
              tagSet.add(tagId);
          }
          tagDao.updateTagList(userBookId, tagSet);
      }
      
      // Returns the book ID
      JSONObject response = new JSONObject();
      response.put("id", userBookId);
      return Response.ok().entity(response).build();
  }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(
            @PathParam("id") String userBookId) throws JSONException {
                if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Fetch the user book
        UserBookDao userBookDao = new UserBookDao();
        UserBook userBook = userBookDao.getUserBook(userBookId, principal.getId());
        if (userBook == null) {
            throw new ClientException(BOOKNOTFOUND, BOOKNOTFOUNDMESSAGE + userBookId);
        }
        
        // Fetch the book
        BookDao bookDao = new BookDao();
        Book bookDb = bookDao.getById(userBook.getBookId());
        
        // Return book data
        JSONObject book = new JSONObject();
        book.put("id", userBook.getId());
        book.put("title", bookDb.getTitle());
        book.put("subtitle", bookDb.getSubtitle());
        book.put("author", bookDb.getAuthor());
        book.put("page_count", bookDb.getPageCount());
        book.put("description", bookDb.getDescription());
        book.put("isbn10", bookDb.getIsbn10());
        book.put("isbn13", bookDb.getIsbn13());
        book.put("language", bookDb.getLanguage());
        if (bookDb.getPublishDate() != null) {
            book.put("publish_date", bookDb.getPublishDate().getTime());
        }
        book.put("create_date", userBook.getCreateDate().getTime());
        if (userBook.getReadDate() != null) {
            book.put("read_date", userBook.getReadDate().getTime());
        }
        
        // Add tags
        TagDao tagDao = new TagDao();
        List<TagDto> tagDtoList = tagDao.getByUserBookId(userBookId);
        List<JSONObject> tags = new ArrayList<>();
        for (TagDto tagDto : tagDtoList) {
            JSONObject tag = new JSONObject();
            tag.put("id", tagDto.getId());
            tag.put("name", tagDto.getName());
            tag.put("color", tagDto.getColor());
            tags.add(tag);
        }
        book.put("tags", tags);
        
        return Response.ok().entity(book).build();
    }

    @POST
    @Path("read")
    @Produces(MediaType.APPLICATION_JSON)
    public Response read(
            @PathParam("id") final String userBookId,
            @FormParam("read") boolean read) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the user book
        UserBookDao userBookDao = new UserBookDao();
        UserBook userBook = userBookDao.getUserBook(userBookId, principal.getId());
        
        // Update the read date
        userBook.setReadDate(read ? new Date() : null);
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }

    @POST
    @Path("cover")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateCover(
            @PathParam("id") String userBookId,
            @FormParam("url") String imageUrl) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the user book
        UserBookDao userBookDao = new UserBookDao();
        UserBook userBook = userBookDao.getUserBook(userBookId, principal.getId());
        if (userBook == null) {
            throw new ClientException(BOOKNOTFOUND, BOOKNOTFOUNDMESSAGE + userBookId);
        }
        
        // Get the book
        BookDao bookDao = new BookDao();
        Book book = bookDao.getById(userBook.getBookId());

        // Download the new cover
        try {
            AppContext.getInstance().getBookDataService().downloadThumbnail(book, imageUrl);
        } catch (Exception e) {
            throw new ClientException("DownloadCoverError", "Error downloading the cover image");
        }
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put(STATUS, "ok");
        return Response.ok(response).build();
    }

    @GET
    @Path("cover")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response cover(
            @PathParam("id") final String userBookId) throws JSONException {
        // Get the user book
        UserBookDao userBookDao = new UserBookDao();
        UserBook userBook = userBookDao.getUserBook(userBookId);
        
        // Get the cover image
        File file = Paths.get(DirectoryUtil.getBookDirectory().getPath(), userBook.getBookId()).toFile();
        InputStream inputStream = null;
        try {
            if (file.exists()) {
                inputStream = new FileInputStream(file);
            } else {
                inputStream = new FileInputStream(new File(getClass().getResource("/dummy.png").getFile()));
            }
        } catch (FileNotFoundException e) {
            throw new ServerException("FileNotFound", "Cover file not found", e);
        }

        return Response.ok(inputStream)
                .header("Content-Type", "image/jpeg")
                .header("Expires", new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z").format(new Date().getTime() + 3600000))
                .build();
    }
}