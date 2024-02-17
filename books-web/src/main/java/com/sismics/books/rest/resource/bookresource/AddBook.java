package com.sismics.books.rest.resource.bookresource;

import java.util.Date;

import javax.ws.rs.FormParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sismics.books.core.dao.jpa.BookDao;
import com.sismics.books.core.dao.jpa.UserBookDao;
import com.sismics.books.core.model.context.AppContext;
import com.sismics.books.core.model.jpa.Book;
import com.sismics.books.core.model.jpa.UserBook;
import com.sismics.books.rest.resource.BaseResource;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.util.ValidationUtil;

@Path("/book")
public class AddBook extends BaseResource{
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  public Response add(
          @FormParam("isbn") String isbn) throws JSONException {
      if (!authenticate()) {
          throw new ForbiddenClientException();
      }
      
      // Validate input data
      ValidationUtil.validateRequired(isbn, "isbn");
      
      // Fetch the book
      BookDao bookDao = new BookDao();
      Book book = bookDao.getByIsbn(isbn);
      if (book == null) {
          // Try to get the book from a public API
          try {
              book = AppContext.getInstance().getBookDataService().searchBook(isbn);
          } catch (Exception e) {
              throw new ClientException("BookNotFound", e.getCause().getMessage(), e);
          }
          
          // Save the new book in database
          bookDao.create(book);
      }
      
      // Create the user book if needed
      UserBookDao userBookDao = new UserBookDao();
      UserBook userBook = userBookDao.getByBook(book.getId(), principal.getId());
      if (userBook == null) {
          userBook = new UserBook();
          userBook.setUserId(principal.getId());
          userBook.setBookId(book.getId());
          userBook.setCreateDate(new Date());
          userBookDao.create(userBook);
      } else {
          throw new ClientException("BookAlreadyAdded", "Book already added");
      }
      
      JSONObject response = new JSONObject();
      response.put("id", userBook.getId());
      return Response.ok().entity(response).build();
  }
} 
