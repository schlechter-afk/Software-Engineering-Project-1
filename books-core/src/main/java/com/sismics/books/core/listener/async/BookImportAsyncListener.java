package com.sismics.books.core.listener.async;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Strings;
import com.google.common.eventbus.Subscribe;
import com.sismics.books.core.dao.jpa.BookDao;
import com.sismics.books.core.dao.jpa.TagDao;
import com.sismics.books.core.dao.jpa.UserBookDao;
import com.sismics.books.core.dao.jpa.dto.TagDto;
import com.sismics.books.core.event.BookImportedEvent;
import com.sismics.books.core.model.context.AppContext;
import com.sismics.books.core.model.jpa.Book;
import com.sismics.books.core.model.jpa.Tag;
import com.sismics.books.core.model.jpa.UserBook;
import com.sismics.books.core.util.TransactionUtil;
import com.sismics.books.core.util.math.MathUtil;

/**
 * Listener on books import request.
 * 
 * @author bgamard
 */
public class BookImportAsyncListener {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(BookImportAsyncListener.class);

    private Book fetchBookFromDataBase(String isbn, BookDao bookDao) {
        Book book = bookDao.getByIsbn(isbn);
        if (book == null) {
            // Try to get the book from a public API
            try {
                book = AppContext.getInstance().getBookDataService().searchBook(isbn);
            } catch (Exception e) {
                return null;
            }
            
            // Save the new book in database
            bookDao.create(book);
        }   
        return book;
    }

    private UserBook createUserBook(String[] line, BookImportedEvent bookImportedEvent, Book book, UserBookDao userBookDao, DateTimeFormatter formatter) {
        UserBook userBook = userBookDao.getByBook(book.getId(), bookImportedEvent.getUser().getId());
        if (userBook == null) {
            userBook = new UserBook();
            userBook.setUserId(bookImportedEvent.getUser().getId());
            userBook.setBookId(book.getId());
            userBook.setCreateDate(new Date());
            if (!Strings.isNullOrEmpty(line[14])) {
                userBook.setReadDate(formatter.parseDateTime(line[14]).toDate());
            }
            if (!Strings.isNullOrEmpty(line[15])) {
                userBook.setCreateDate(formatter.parseDateTime(line[15]).toDate());
            }
            userBookDao.create(userBook);
        }
        return userBook;
    }

    private Set<String> createTagsforUserBook(String[] line, BookImportedEvent bookImportedEvent, TagDao tagDao, UserBook userBook) {
        // Create tags
        String[] bookshelfArray = line[16].split(",");
        Set<String> tagIdSet = new HashSet<String>();
        for (String bookshelf : bookshelfArray) {
            bookshelf = bookshelf.trim();
            if (Strings.isNullOrEmpty(bookshelf)) {
                continue;
            }
            
            Tag tag = tagDao.getByName(bookImportedEvent.getUser().getId(), bookshelf);
            if (tag == null) {
                tag = new Tag();
                tag.setName(bookshelf);
                tag.setColor(MathUtil.randomHexColor());
                tag.setUserId(bookImportedEvent.getUser().getId());
                tagDao.create(tag);
            }
            
            tagIdSet.add(tag.getId());
        }
    
        return tagIdSet;
    }

    private void processLineEntry(String[] line, BookImportedEvent bookImportedEvent, BookDao bookDao, UserBookDao userBookDao, TagDao tagDao,DateTimeFormatter formatter)  throws Exception {
        try {
            // Retrieve ISBN number
            String isbn = Strings.isNullOrEmpty(line[6]) ? line[5] : line[6];
            if (Strings.isNullOrEmpty(isbn)) {
                log.warn("No ISBN number for Goodreads book ID: {}", line[0]);
                return;
            }

            // Fetch the book from database if it exists
            Book book = fetchBookFromDataBase(isbn, bookDao);
            if (book == null) {
                return; //Unsuccessful
            }

            UserBook userBook = createUserBook(line, bookImportedEvent, book, userBookDao, formatter);
            Set<String> tagIdSet = createTagsforUserBook(line, bookImportedEvent, tagDao, userBook);

            // Add tags to the user book
            if (!tagIdSet.isEmpty()) {
                List<TagDto> tagDtoList = tagDao.getByUserBookId(userBook.getId());
                for (TagDto tagDto : tagDtoList) {
                    tagIdSet.add(tagDto.getId());
                }
                tagDao.updateTagList(userBook.getId(), tagIdSet);
            }
                            
            TransactionUtil.commit();
        } catch (Exception e) {
            log.error("Error parsing CSV line", e);
        }           
    }

    private void processCSV(CSVReader reader, BookImportedEvent bookImportedEvent) throws Exception {
        BookDao bookDao = new BookDao();
        UserBookDao userBookDao = new UserBookDao();
        TagDao tagDao = new TagDao();
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy/MM/dd");

        String [] line;
        while ((line = reader.readNext()) != null) {
            if (line[0].equals("Book Id")) {
                // Skip header
                continue;
            }

            processLineEntry(line, bookImportedEvent, bookDao, userBookDao, tagDao, formatter);
        }
          
    }

    // /**
    //  * Process the event.
    //  * 
    //  * @param bookImportedEvent Book imported event
    //  * @throws Exception
    //  */
    @Subscribe
    public void on(final BookImportedEvent bookImportedEvent) throws Exception {
        if (log.isInfoEnabled()) {
            log.info(MessageFormat.format("Books import requested event: {0}", bookImportedEvent.toString()));
        }
        
        // Create books and tags
        TransactionUtil.handle(new Runnable() {
            @Override
            public void run() {
                try {
                    CSVReader reader = new CSVReader(new FileReader(bookImportedEvent.getImportFile()));
                    processCSV(reader, bookImportedEvent);
                } catch (FileNotFoundException e) {
                    log.error("Unable to read CSV file", e);
                } catch (Exception e) {
                    log.error("Error parsing CSV file");
                }
            }
        });
    }
}
