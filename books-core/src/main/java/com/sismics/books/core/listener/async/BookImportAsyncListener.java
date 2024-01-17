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

    /**
     * Process the event.
     * 
     * @param bookImportedEvent Book imported event
     * @throws Exception
     */
    @Subscribe
    public void on(final BookImportedEvent bookImportedEvent) throws Exception {
        if (log.isInfoEnabled()) {
            log.info(MessageFormat.format("Books import requested event: {0}", bookImportedEvent.toString()));
        }
        
        // Create books and tags
        TransactionUtil.handle(new Runnable() {
            @Override
            public void run() {
                CSVReader reader = null;
                BookDao bookDao = new BookDao();
                UserBookDao userBookDao = new UserBookDao();
                TagDao tagDao = new TagDao();
                try {
                    reader = new CSVReader(new FileReader(bookImportedEvent.getImportFile()));
                } catch (FileNotFoundException e) {
                    log.error("Unable to read CSV file", e);
                }
                
                // Goodreads date format
                DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy/MM/dd");
                
                String [] line;
                try {
                    while ((line = reader.readNext()) != null) {
                        if (line[0].equals("Book Id")) {
                            // Skip header
                            continue;
                        }
                        
                        // Retrieve ISBN number
                        String isbn = Strings.isNullOrEmpty(line[6]) ? line[5] : line[6];
                        if (Strings.isNullOrEmpty(isbn)) {
                            log.warn("No ISBN number for Goodreads book ID: " + line[0]);
                            continue;
                        }
                        
                        // Fetch the book from database if it exists
                        Book book = bookDao.getByIsbn(isbn);
                        if (book == null) {
                            // Try to get the book from a public API
                            try {
                                book = AppContext.getInstance().getBookDataService().searchBook(isbn);
                            } catch (Exception e) {
                                continue;
                            }
                            
                            // Save the new book in database
                            bookDao.create(book);
                        }
                        
                        // Create a new user book if needed
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
                        
                        // Add tags to the user book
                        if (tagIdSet.size() > 0) {
                            List<TagDto> tagDtoList = tagDao.getByUserBookId(userBook.getId());
                            for (TagDto tagDto : tagDtoList) {
                                tagIdSet.add(tagDto.getId());
                            }
                            tagDao.updateTagList(userBook.getId(), tagIdSet);
                        }
                        
                        TransactionUtil.commit();
                    }
                } catch (Exception e) {
                    log.error("Error parsing CSV line", e);
                }
            }
        });
    }
}
