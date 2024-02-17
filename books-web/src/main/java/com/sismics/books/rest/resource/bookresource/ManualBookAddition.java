package com.sismics.books.rest.resource.bookresource;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.FormParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;
import java.text.MessageFormat;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.google.common.base.Strings;
import com.sismics.books.core.dao.jpa.BookDao;
import com.sismics.books.core.dao.jpa.TagDao;
import com.sismics.books.core.dao.jpa.UserBookDao;
import com.sismics.books.core.model.jpa.Book;
import com.sismics.books.core.model.jpa.Tag;
import com.sismics.books.core.model.jpa.UserBook;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.util.ValidationUtil;

import com.sismics.books.rest.resource.BaseResource;

@Path("/book/manual")
public class ManualBookAddition extends BaseResource {
    public static class BookParams {
        private String title;
        private String subtitle;
        private String author;
        private String description;
        private String isbn10;
        private String isbn13;
        private Long pageCount;
        private String language;
        private String publishDateStr;
        private Date publishDate;
        private List<String> tagList;
    }

    /**
     * Add a book manually.
     *
     * @param params Book parameters
     * @return Response
     * @throws JSONException
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
        public Response add(
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
        
        BookParams valParams = new BookParams();

        valParams.title = title;
        valParams.subtitle = subtitle;
        valParams.author = author;
        valParams.description = description;
        valParams.isbn10 = isbn10;
        valParams.isbn13 = isbn13;
        valParams.language = language;
        valParams.pageCount = pageCount;
        valParams.publishDateStr = publishDateStr;

        valParams = validateInputData(valParams);

        BookDao bookDao = new BookDao();
        checkIfBookExists(valParams.isbn10, valParams.isbn13, bookDao);

        Book book = createBook(valParams);
        UserBook userBook = createUserBook(book);

        updateTags(valParams.tagList, userBook);

        JSONObject response = new JSONObject();
        response.put("id", userBook.getId());
        return Response.ok().entity(response).build();
    }
    
    private BookParams validateInputData(BookParams params) throws JSONException{
        params.title = ValidationUtil.validateLength(params.title, "title", 1, 255, false);
        params.subtitle = ValidationUtil.validateLength(params.subtitle, "subtitle", 1, 255, true);
        params.author = ValidationUtil.validateLength(params.author, "author", 1, 255, false);
        params.description = ValidationUtil.validateLength(params.description, "description", 1, 4000, true);
        params.isbn10 = ValidationUtil.validateLength(params.isbn10, "isbn10", 10, 10, true);
        params.isbn13 = ValidationUtil.validateLength(params.isbn13, "isbn13", 13, 13, true);
        params.language = ValidationUtil.validateLength(params.language, "language", 2, 2, true);
        params.publishDate = ValidationUtil.validateDate(params.publishDateStr, "publish_date", false);

        if (Strings.isNullOrEmpty(params.isbn10) && Strings.isNullOrEmpty(params.isbn13)) {
            throw new ClientException("ValidationError", "At least one ISBN number is mandatory");
        }

        return params;
    }

    private void checkIfBookExists(String isbn10, String isbn13, BookDao bookDao) throws JSONException{
        Book bookIsbn10 = bookDao.getByIsbn(isbn10);
        Book bookIsbn13 = bookDao.getByIsbn(isbn13);
        if (bookIsbn10 != null || bookIsbn13 != null) {
            throw new ClientException("BookAlreadyAdded", "Book already added");
        }
    }

    private Book createBook(BookParams params) {
        Book book = new Book();
        book.setId(UUID.randomUUID().toString());

        if (params.title != null) {
            book.setTitle(params.title);
        }
        if (params.subtitle != null) {
            book.setSubtitle(params.subtitle);
        }
        if (params.author != null) {
            book.setAuthor(params.author);
        }
        if (params.description != null) {
            book.setDescription(params.description);
        }
        if (params.isbn10 != null) {
            book.setIsbn10(params.isbn10);
        }
        if (params.isbn13 != null) {
            book.setIsbn13(params.isbn13);
        }
        if (params.pageCount != null) {
            book.setPageCount(params.pageCount);
        }
        if (params.language != null) {
            book.setLanguage(params.language);
        }
        if (params.publishDate != null) {
            book.setPublishDate(params.publishDate);
        }

        new BookDao().create(book);
        return book;
    }

    private UserBook createUserBook(Book book) {
        UserBookDao userBookDao = new UserBookDao();
        UserBook userBook = new UserBook();
        userBook.setUserId(principal.getId());
        userBook.setBookId(book.getId());
        userBook.setCreateDate(new Date());
        userBookDao.create(userBook);

        return userBook;
    }

    private void updateTags (List<String> tagList, UserBook userBook) throws JSONException{
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
            tagDao.updateTagList(userBook.getId(), tagSet);
        }
    }
}
