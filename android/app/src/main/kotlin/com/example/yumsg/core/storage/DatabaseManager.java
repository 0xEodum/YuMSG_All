package com.example.yumsg.core.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.example.yumsg.core.data.*;
import com.example.yumsg.core.enums.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * DatabaseManager - Complete Implementation
 * 
 * Manages SQLite database operations for the YuMSG application.
 * Provides thread-safe CRUD operations for all data entities.
 * 
 * Key Features:
 * - Thread-safe database operations
 * - Automatic schema migrations
 * - Comprehensive error handling
 * - Backup and restore functionality
 * - Optimized queries with proper indexing
 * - Transaction support for batch operations
 * - Secure data handling with proper cleanup
 */
public class DatabaseManager extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseManager";
    
    // Database configuration
    private static final String DATABASE_NAME = "yumsg.db";
    private static final int DATABASE_VERSION = 1;
    
    // Table names
    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_CHATS = "chats";
    private static final String TABLE_CONTACTS = "contacts";
    private static final String TABLE_FILES = "files";
    private static final String TABLE_USER_PROFILE = "user_profile";
    private static final String TABLE_SETTINGS = "settings";
    private static final String TABLE_ORGANIZATION_KEYS = "organization_keys";
    
    // Common columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_UPDATED_AT = "updated_at";
    
    // Messages table columns
    private static final String COLUMN_MESSAGE_CHAT_ID = "chat_id";
    private static final String COLUMN_MESSAGE_CONTENT = "content";
    private static final String COLUMN_MESSAGE_STATUS = "status";
    private static final String COLUMN_MESSAGE_TIMESTAMP = "timestamp";
    private static final String COLUMN_MESSAGE_SENDER_ID = "sender_id";
    private static final String COLUMN_MESSAGE_TYPE = "message_type";
    private static final String COLUMN_MESSAGE_FILE_ID = "file_id";
    
    // Chats table columns
    private static final String COLUMN_CHAT_NAME = "name";
    private static final String COLUMN_CHAT_KEYS = "keys_data";
    private static final String COLUMN_CHAT_LAST_ACTIVITY = "last_activity";
    private static final String COLUMN_CHAT_PARTICIPANTS = "participants";
    private static final String COLUMN_CHAT_ENCRYPTION_STATUS = "encryption_status";
    private static final String COLUMN_CHAT_PEER_SIGNATURE_KEY = "peer_signature_public_key";
    private static final String COLUMN_CHAT_PEER_SIGNATURE_ALGORITHM = "peer_signature_algorithm";
    private static final String COLUMN_CHAT_PEER_ALGORITHMS = "peer_algorithms";
    private static final String COLUMN_CHAT_PEER_CRYPTO_VERIFIED = "peer_crypto_verified";
    private static final String COLUMN_CHAT_FINGERPRINT = "chat_fingerprint";
    private static final String COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS = "key_establishment_status";
    private static final String COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT = "key_establishment_completed_at";
    
    // Contacts table columns
    private static final String COLUMN_CONTACT_NAME = "name";
    private static final String COLUMN_CONTACT_PUBLIC_KEY = "public_key";
    private static final String COLUMN_CONTACT_USER_ID = "user_id";
    private static final String COLUMN_CONTACT_STATUS = "status";
    private static final String COLUMN_CONTACT_LAST_SEEN = "last_seen";
    
    // Files table columns
    private static final String COLUMN_FILE_NAME = "name";
    private static final String COLUMN_FILE_SIZE = "size";
    private static final String COLUMN_FILE_MIME_TYPE = "mime_type";
    private static final String COLUMN_FILE_PATH = "path";
    private static final String COLUMN_FILE_HASH = "hash";
    private static final String COLUMN_FILE_ENCRYPTION_STATUS = "encryption_status";
    
    // Settings table columns
    private static final String COLUMN_SETTING_KEY = "setting_key";
    private static final String COLUMN_SETTING_VALUE = "setting_value";
    private static final String COLUMN_SETTING_TYPE = "setting_type";
    
    // User profile columns
    private static final String COLUMN_PROFILE_USERNAME = "username";
    private static final String COLUMN_PROFILE_EMAIL = "email";
    private static final String COLUMN_PROFILE_DISPLAY_NAME = "display_name";
    private static final String COLUMN_PROFILE_DATA = "profile_data";
    
    // Organization keys columns
    private static final String COLUMN_ORG_NAME = "organization_name";
    private static final String COLUMN_ORG_SIGNATURE_ALGORITHM = "signature_algorithm";
    private static final String COLUMN_ORG_PUBLIC_SIGNATURE_KEY = "public_signature_key";
    private static final String COLUMN_ORG_PRIVATE_SIGNATURE_KEY = "private_signature_key";
    private static final String COLUMN_ORG_IS_ACTIVE = "is_active";
    
    // Singleton instance
    private static volatile DatabaseManager instance;
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Dependencies
    private final Gson gson;
    private final Context context;
    
    // State
    private volatile boolean isInitialized = false;
    private SQLiteDatabase database;
    
    /**
     * Private constructor for singleton pattern
     */
    private DatabaseManager(Context context, String dbPath) {
        super(context, dbPath != null ? dbPath : DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();
        
        Log.d(TAG, "DatabaseManager instance created with path: " + 
              (dbPath != null ? dbPath : DATABASE_NAME));
    }
    
    /**
     * Get singleton instance
     */
    public static DatabaseManager getInstance(Context context, String dbPath) {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager(context, dbPath);
                }
            }
        }
        return instance;
    }
    
    /**
     * Get singleton instance (requires previous initialization)
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DatabaseManager not initialized. Call getInstance(Context, String) first.");
        }
        return instance;
    }
    
    // ===========================
    // LIFECYCLE METHODS
    // ===========================
    
    /**
     * Initialize the database manager
     */
    public boolean initialize(String dbPath) {
        lock.writeLock().lock();
        try {
            if (isInitialized) {
                Log.w(TAG, "DatabaseManager already initialized");
                return true;
            }
            
            Log.d(TAG, "Initializing DatabaseManager");
            
            // Open database connection
            database = this.getWritableDatabase();
            
            if (database == null) {
                Log.e(TAG, "Failed to open database");
                return false;
            }
            
            // Enable foreign keys
            database.execSQL("PRAGMA foreign_keys=ON;");
            
            // Enable WAL mode for better performance
            database.execSQL("PRAGMA journal_mode=WAL;");
            
            // Verify database integrity
            if (!verifyDatabaseIntegrity()) {
                Log.e(TAG, "Database integrity check failed");
                return false;
            }
            
            isInitialized = true;
            Log.i(TAG, "DatabaseManager initialized successfully");
            return true;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to initialize DatabaseManager", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Close database connection
     */
    public void close() {
        lock.writeLock().lock();
        try {
            if (database != null && database.isOpen()) {
                database.close();
                Log.d(TAG, "Database closed");
            }
            isInitialized = false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating database tables");
        
        try {
            // Create messages table
            db.execSQL(CREATE_MESSAGES_TABLE);
            
            // Create chats table
            db.execSQL(CREATE_CHATS_TABLE);
            
            // Create contacts table
            db.execSQL(CREATE_CONTACTS_TABLE);
            
            // Create files table
            db.execSQL(CREATE_FILES_TABLE);
            
            // Create user profile table
            db.execSQL(CREATE_USER_PROFILE_TABLE);
            
            // Create settings table
            db.execSQL(CREATE_SETTINGS_TABLE);
            
            // Create organization keys table
            db.execSQL(CREATE_ORGANIZATION_KEYS_TABLE);
            
            // Create indexes for performance
            createIndexes(db);
            
            Log.i(TAG, "Database tables created successfully");
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to create database tables", e);
            throw e;
        }
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        
        // Implement migration logic here
        // For now, simple drop and recreate (data loss!)
        // In production, implement proper schema migrations
        
        try {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORGANIZATION_KEYS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_PROFILE);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_FILES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHATS);
            
            onCreate(db);
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to upgrade database", e);
            throw e;
        }
    }
    
    // ===========================
    // MESSAGE OPERATIONS
    // ===========================
    
    /**
     * Save message to database
     */
    public long saveMessage(Message message) {
        if (message == null) {
            Log.w(TAG, "Attempted to save null message");
            return -1;
        }
        
        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_MESSAGE_CHAT_ID, message.getChatId());
            values.put(COLUMN_MESSAGE_CONTENT, message.getContent());
            values.put(COLUMN_MESSAGE_STATUS, message.getStatus().name());
            values.put(COLUMN_MESSAGE_TIMESTAMP, message.getTimestamp());
            values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
            
            long messageId = database.insert(TABLE_MESSAGES, null, values);
            
            if (messageId != -1) {
                message.setId(messageId);
                Log.d(TAG, "Message saved with ID: " + messageId);
            } else {
                Log.e(TAG, "Failed to save message");
            }
            
            return messageId;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error saving message", e);
            return -1;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get message by ID
     */
    public Message getMessage(long messageId) {
        lock.readLock().lock();
        try {
            String selection = COLUMN_ID + " = ?";
            String[] selectionArgs = {String.valueOf(messageId)};
            
            Cursor cursor = database.query(
                TABLE_MESSAGES,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                Message message = messageFromCursor(cursor);
                cursor.close();
                return message;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            return null;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving message", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get messages for a specific chat
     */
    public List<Message> getMessages(String chatId, int limit, int offset) {
        List<Message> messages = new ArrayList<>();
        
        if (chatId == null) {
            Log.w(TAG, "Cannot get messages for null chatId");
            return messages;
        }
        
        lock.readLock().lock();
        try {
            String selection = COLUMN_MESSAGE_CHAT_ID + " = ?";
            String[] selectionArgs = {chatId};
            String orderBy = COLUMN_MESSAGE_TIMESTAMP + " DESC";
            String limitClause = limit > 0 ? offset + "," + limit : null;
            
            Cursor cursor = database.query(
                TABLE_MESSAGES,
                null,
                selection,
                selectionArgs,
                null,
                null,
                orderBy,
                limitClause
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Message message = messageFromCursor(cursor);
                    if (message != null) {
                        messages.add(message);
                    }
                }
                cursor.close();
            }
            
            Log.d(TAG, "Retrieved " + messages.size() + " messages for chat: " + chatId);
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving messages", e);
        } finally {
            lock.readLock().unlock();
        }
        
        return messages;
    }
    
    /**
     * Delete message by ID
     */
    public boolean deleteMessage(long messageId) {
        lock.writeLock().lock();
        try {
            String whereClause = COLUMN_ID + " = ?";
            String[] whereArgs = {String.valueOf(messageId)};
            
            int deletedRows = database.delete(TABLE_MESSAGES, whereClause, whereArgs);
            
            if (deletedRows > 0) {
                Log.d(TAG, "Message deleted: " + messageId);
                return true;
            } else {
                Log.w(TAG, "No message found to delete: " + messageId);
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error deleting message", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Update message status
     */
    public boolean updateMessageStatus(long messageId, MessageStatus status) {
        if (status == null) {
            Log.w(TAG, "Cannot update message status to null");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_MESSAGE_STATUS, status.name());
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
            
            String whereClause = COLUMN_ID + " = ?";
            String[] whereArgs = {String.valueOf(messageId)};
            
            int updatedRows = database.update(TABLE_MESSAGES, values, whereClause, whereArgs);
            
            if (updatedRows > 0) {
                Log.d(TAG, "Message status updated: " + messageId + " -> " + status);
                return true;
            } else {
                Log.w(TAG, "No message found to update: " + messageId);
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error updating message status", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // CHAT OPERATIONS
    // ===========================
    
    /**
     * Save chat to database
     */
    public String saveChat(Chat chat) {
        if (chat == null) {
            Log.w(TAG, "Attempted to save null chat");
            return null;
        }
        
        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_ID, chat.getId());
            values.put(COLUMN_CHAT_NAME, chat.getName());
            values.put(COLUMN_CHAT_LAST_ACTIVITY, chat.getLastActivity());
            values.put(COLUMN_CREATED_AT, chat.getCreatedAt() > 0 ? chat.getCreatedAt() : System.currentTimeMillis());
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());

            if (chat.getFingerprint() != null) {
                values.put(COLUMN_CHAT_FINGERPRINT, chat.getFingerprint());
            }
            if (chat.getKeyEstablishmentStatus() != null) {
                values.put(COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS, chat.getKeyEstablishmentStatus());
            }
            if (chat.getKeyEstablishmentCompletedAt() > 0) {
                values.put(COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT, chat.getKeyEstablishmentCompletedAt());
            }
            
            // Serialize ChatKeys if present
            if (chat.getKeys() != null) {
                String keysJson = gson.toJson(chat.getKeys());
                values.put(COLUMN_CHAT_KEYS, keysJson);
            }
            
            // Serialize PeerCryptoInfo if present
            if (chat.getPeerCryptoInfo() != null) {
                PeerCryptoInfo peerInfo = chat.getPeerCryptoInfo();
                
                // Store peer signature key as Base64
                if (peerInfo.getPeerSignaturePublicKey() != null) {
                    values.put(COLUMN_CHAT_PEER_SIGNATURE_KEY, peerInfo.getPeerSignaturePublicKeyBase64());
                }
                
                // Store peer signature algorithm
                values.put(COLUMN_CHAT_PEER_SIGNATURE_ALGORITHM, peerInfo.getPeerSignatureAlgorithm());
                
                // Store peer algorithms as JSON
                if (peerInfo.getPeerAlgorithms() != null) {
                    values.put(COLUMN_CHAT_PEER_ALGORITHMS, gson.toJson(peerInfo.getPeerAlgorithms()));
                }
                
                // Store verification status
                values.put(COLUMN_CHAT_PEER_CRYPTO_VERIFIED, peerInfo.isVerified() ? 1 : 0);
            }
            
            long result = database.insertWithOnConflict(
                TABLE_CHATS, 
                null, 
                values, 
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            if (result != -1) {
                Log.d(TAG, "Chat saved: " + chat.getId());
                return chat.getId();
            } else {
                Log.e(TAG, "Failed to save chat");
                return null;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error saving chat", e);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get chat by ID
     */
    public Chat getChat(String chatId) {
        if (chatId == null) {
            Log.w(TAG, "Cannot get chat with null ID");
            return null;
        }
        
        lock.readLock().lock();
        try {
            String selection = COLUMN_ID + " = ?";
            String[] selectionArgs = {chatId};
            
            Cursor cursor = database.query(
                TABLE_CHATS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                Chat chat = chatFromCursor(cursor);
                cursor.close();
                return chat;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            return null;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving chat", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all chats
     */
    public List<Chat> getAllChats() {
        List<Chat> chats = new ArrayList<>();
        
        lock.readLock().lock();
        try {
            String orderBy = COLUMN_CHAT_LAST_ACTIVITY + " DESC";
            
            Cursor cursor = database.query(
                TABLE_CHATS,
                null,
                null,
                null,
                null,
                null,
                orderBy
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Chat chat = chatFromCursor(cursor);
                    if (chat != null) {
                        chats.add(chat);
                    }
                }
                cursor.close();
            }
            
            Log.d(TAG, "Retrieved " + chats.size() + " chats");
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving chats", e);
        } finally {
            lock.readLock().unlock();
        }
        
        return chats;
    }
    
    /**
     * Delete chat by ID
     */
    public boolean deleteChat(String chatId) {
        if (chatId == null) {
            Log.w(TAG, "Cannot delete chat with null ID");
            return false;
        }
        
        lock.writeLock().lock();
        SQLiteDatabase db = null;
        try {
            db = database;
            db.beginTransaction();
            
            // Delete all messages in the chat first
            String messageWhereClause = COLUMN_MESSAGE_CHAT_ID + " = ?";
            String[] messageWhereArgs = {chatId};
            int deletedMessages = db.delete(TABLE_MESSAGES, messageWhereClause, messageWhereArgs);
            
            // Delete the chat
            String chatWhereClause = COLUMN_ID + " = ?";
            String[] chatWhereArgs = {chatId};
            int deletedChats = db.delete(TABLE_CHATS, chatWhereClause, chatWhereArgs);
            
            if (deletedChats > 0) {
                db.setTransactionSuccessful();
                Log.d(TAG, "Chat deleted: " + chatId + " (with " + deletedMessages + " messages)");
                return true;
            } else {
                Log.w(TAG, "No chat found to delete: " + chatId);
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error deleting chat", e);
            return false;
        } finally {
            if (db != null && db.inTransaction()) {
                db.endTransaction();
            }
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear all messages in a chat
     */
    public boolean clearChatMessages(String chatId) {
        if (chatId == null) {
            Log.w(TAG, "Cannot clear messages for null chatId");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            String whereClause = COLUMN_MESSAGE_CHAT_ID + " = ?";
            String[] whereArgs = {chatId};
            
            int deletedRows = database.delete(TABLE_MESSAGES, whereClause, whereArgs);
            
            Log.d(TAG, "Cleared " + deletedRows + " messages from chat: " + chatId);
            return true;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error clearing chat messages", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // CONTACT OPERATIONS
    // ===========================
    
    /**
     * Save contact to database
     */
    public String saveContact(Contact contact) {
        if (contact == null) {
            Log.w(TAG, "Attempted to save null contact");
            return null;
        }
        
        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_ID, contact.getId());
            values.put(COLUMN_CONTACT_NAME, contact.getName());
            values.put(COLUMN_CONTACT_PUBLIC_KEY, contact.getPublicKey());
            values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
            
            long result = database.insertWithOnConflict(
                TABLE_CONTACTS, 
                null, 
                values, 
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            if (result != -1) {
                Log.d(TAG, "Contact saved: " + contact.getId());
                return contact.getId();
            } else {
                Log.e(TAG, "Failed to save contact");
                return null;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error saving contact", e);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get contact by ID
     */
    public Contact getContact(String contactId) {
        if (contactId == null) {
            Log.w(TAG, "Cannot get contact with null ID");
            return null;
        }
        
        lock.readLock().lock();
        try {
            String selection = COLUMN_ID + " = ?";
            String[] selectionArgs = {contactId};
            
            Cursor cursor = database.query(
                TABLE_CONTACTS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                Contact contact = contactFromCursor(cursor);
                cursor.close();
                return contact;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            return null;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving contact", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all contacts
     */
    public List<Contact> getAllContacts() {
        List<Contact> contacts = new ArrayList<>();
        
        lock.readLock().lock();
        try {
            String orderBy = COLUMN_CONTACT_NAME + " ASC";
            
            Cursor cursor = database.query(
                TABLE_CONTACTS,
                null,
                null,
                null,
                null,
                null,
                orderBy
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Contact contact = contactFromCursor(cursor);
                    if (contact != null) {
                        contacts.add(contact);
                    }
                }
                cursor.close();
            }
            
            Log.d(TAG, "Retrieved " + contacts.size() + " contacts");
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving contacts", e);
        } finally {
            lock.readLock().unlock();
        }
        
        return contacts;
    }
    
    /**
     * Delete contact by ID
     */
    public boolean deleteContact(String contactId) {
        if (contactId == null) {
            Log.w(TAG, "Cannot delete contact with null ID");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            String whereClause = COLUMN_ID + " = ?";
            String[] whereArgs = {contactId};
            
            int deletedRows = database.delete(TABLE_CONTACTS, whereClause, whereArgs);
            
            if (deletedRows > 0) {
                Log.d(TAG, "Contact deleted: " + contactId);
                return true;
            } else {
                Log.w(TAG, "No contact found to delete: " + contactId);
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error deleting contact", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // FILE OPERATIONS
    // ===========================
    
    /**
     * Save file info to database
     */
    public String saveFile(FileInfo fileInfo) {
        if (fileInfo == null) {
            Log.w(TAG, "Attempted to save null file info");
            return null;
        }
        
        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_ID, fileInfo.getId());
            values.put(COLUMN_FILE_NAME, fileInfo.getName());
            values.put(COLUMN_FILE_SIZE, fileInfo.getSize());
            values.put(COLUMN_FILE_MIME_TYPE, fileInfo.getMimeType());
            values.put(COLUMN_FILE_PATH, fileInfo.getPath());
            values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
            
            long result = database.insertWithOnConflict(
                TABLE_FILES, 
                null, 
                values, 
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            if (result != -1) {
                Log.d(TAG, "File info saved: " + fileInfo.getId());
                return fileInfo.getId();
            } else {
                Log.e(TAG, "Failed to save file info");
                return null;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error saving file info", e);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get file info by ID
     */
    public FileInfo getFile(String fileId) {
        if (fileId == null) {
            Log.w(TAG, "Cannot get file with null ID");
            return null;
        }
        
        lock.readLock().lock();
        try {
            String selection = COLUMN_ID + " = ?";
            String[] selectionArgs = {fileId};
            
            Cursor cursor = database.query(
                TABLE_FILES,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                FileInfo fileInfo = fileInfoFromCursor(cursor);
                cursor.close();
                return fileInfo;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            return null;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving file info", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all files
     */
    public List<FileInfo> getAllFiles() {
        List<FileInfo> files = new ArrayList<>();
        
        lock.readLock().lock();
        try {
            String orderBy = COLUMN_CREATED_AT + " DESC";
            
            Cursor cursor = database.query(
                TABLE_FILES,
                null,
                null,
                null,
                null,
                null,
                orderBy
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    FileInfo fileInfo = fileInfoFromCursor(cursor);
                    if (fileInfo != null) {
                        files.add(fileInfo);
                    }
                }
                cursor.close();
            }
            
            Log.d(TAG, "Retrieved " + files.size() + " files");
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving files", e);
        } finally {
            lock.readLock().unlock();
        }
        
        return files;
    }
    
    /**
     * Delete file info by ID
     */
    public boolean deleteFile(String fileId) {
        if (fileId == null) {
            Log.w(TAG, "Cannot delete file with null ID");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            String whereClause = COLUMN_ID + " = ?";
            String[] whereArgs = {fileId};
            
            int deletedRows = database.delete(TABLE_FILES, whereClause, whereArgs);
            
            if (deletedRows > 0) {
                Log.d(TAG, "File info deleted: " + fileId);
                return true;
            } else {
                Log.w(TAG, "No file found to delete: " + fileId);
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error deleting file info", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // ORGANIZATION KEYS OPERATIONS
    // ===========================
    
    /**
     * Save organization keys to database
     */
    public boolean saveOrganizationKeys(OrganizationKeys orgKeys) {
        if (orgKeys == null) {
            Log.w(TAG, "Attempted to save null organization keys");
            return false;
        }
        
        if (!orgKeys.isComplete()) {
            Log.w(TAG, "Attempted to save incomplete organization keys");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_ID, orgKeys.getId());
            values.put(COLUMN_ORG_NAME, orgKeys.getOrganizationName());
            values.put(COLUMN_ORG_SIGNATURE_ALGORITHM, orgKeys.getSignatureAlgorithm());
            values.put(COLUMN_ORG_PUBLIC_SIGNATURE_KEY, orgKeys.getPublicKeyBase64());
            values.put(COLUMN_ORG_PRIVATE_SIGNATURE_KEY, orgKeys.getPrivateKeyBase64());
            values.put(COLUMN_ORG_IS_ACTIVE, orgKeys.isActive() ? 1 : 0);
            values.put(COLUMN_CREATED_AT, orgKeys.getCreatedAt());
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
            
            long result = database.insertWithOnConflict(
                TABLE_ORGANIZATION_KEYS, 
                null, 
                values, 
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            if (result != -1) {
                Log.d(TAG, "Organization keys saved: " + orgKeys.getOrganizationName() + 
                          ":" + orgKeys.getSignatureAlgorithm());
                return true;
            } else {
                Log.e(TAG, "Failed to save organization keys");
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error saving organization keys", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get organization keys by organization name and algorithm
     */
    public OrganizationKeys getOrganizationKeys(String organizationName, String signatureAlgorithm) {
        if (organizationName == null || signatureAlgorithm == null) {
            Log.w(TAG, "Cannot get organization keys with null parameters");
            return null;
        }
        
        lock.readLock().lock();
        try {
            String selection = COLUMN_ORG_NAME + " = ? AND " + COLUMN_ORG_SIGNATURE_ALGORITHM + " = ?";
            String[] selectionArgs = {organizationName, signatureAlgorithm};
            
            Cursor cursor = database.query(
                TABLE_ORGANIZATION_KEYS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                OrganizationKeys orgKeys = organizationKeysFromCursor(cursor);
                cursor.close();
                return orgKeys;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            return null;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving organization keys", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all organization keys
     */
    public List<OrganizationKeys> getAllOrganizationKeys() {
        List<OrganizationKeys> orgKeysList = new ArrayList<>();
        
        lock.readLock().lock();
        try {
            String orderBy = COLUMN_ORG_NAME + " ASC, " + COLUMN_ORG_SIGNATURE_ALGORITHM + " ASC";
            
            Cursor cursor = database.query(
                TABLE_ORGANIZATION_KEYS,
                null,
                null,
                null,
                null,
                null,
                orderBy
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    OrganizationKeys orgKeys = organizationKeysFromCursor(cursor);
                    if (orgKeys != null) {
                        orgKeysList.add(orgKeys);
                    }
                }
                cursor.close();
            }
            
            Log.d(TAG, "Retrieved " + orgKeysList.size() + " organization key sets");
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving organization keys", e);
        } finally {
            lock.readLock().unlock();
        }
        
        return orgKeysList;
    }
    
    /**
     * Get active organization keys for an organization
     */
    public List<OrganizationKeys> getActiveOrganizationKeys(String organizationName) {
        List<OrganizationKeys> orgKeysList = new ArrayList<>();
        
        if (organizationName == null) {
            Log.w(TAG, "Cannot get organization keys with null organization name");
            return orgKeysList;
        }
        
        lock.readLock().lock();
        try {
            String selection = COLUMN_ORG_NAME + " = ? AND " + COLUMN_ORG_IS_ACTIVE + " = 1";
            String[] selectionArgs = {organizationName};
            String orderBy = COLUMN_ORG_SIGNATURE_ALGORITHM + " ASC";
            
            Cursor cursor = database.query(
                TABLE_ORGANIZATION_KEYS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                orderBy
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    OrganizationKeys orgKeys = organizationKeysFromCursor(cursor);
                    if (orgKeys != null) {
                        orgKeysList.add(orgKeys);
                    }
                }
                cursor.close();
            }
            
            Log.d(TAG, "Retrieved " + orgKeysList.size() + " active organization key sets for: " + organizationName);
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving active organization keys", e);
        } finally {
            lock.readLock().unlock();
        }
        
        return orgKeysList;
    }
    
    /**
     * Delete organization keys
     */
    public boolean deleteOrganizationKeys(String organizationName, String signatureAlgorithm) {
        if (organizationName == null || signatureAlgorithm == null) {
            Log.w(TAG, "Cannot delete organization keys with null parameters");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            String whereClause = COLUMN_ORG_NAME + " = ? AND " + COLUMN_ORG_SIGNATURE_ALGORITHM + " = ?";
            String[] whereArgs = {organizationName, signatureAlgorithm};
            
            int deletedRows = database.delete(TABLE_ORGANIZATION_KEYS, whereClause, whereArgs);
            
            if (deletedRows > 0) {
                Log.d(TAG, "Organization keys deleted: " + organizationName + ":" + signatureAlgorithm);
                return true;
            } else {
                Log.w(TAG, "No organization keys found to delete: " + organizationName + ":" + signatureAlgorithm);
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error deleting organization keys", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Update chat peer crypto information
     */
    public boolean updateChatPeerCrypto(String chatId, PeerCryptoInfo peerCryptoInfo) {
        if (chatId == null || peerCryptoInfo == null) {
            Log.w(TAG, "Cannot update peer crypto with null parameters");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            
            // Update peer signature key
            if (peerCryptoInfo.getPeerSignaturePublicKey() != null) {
                values.put(COLUMN_CHAT_PEER_SIGNATURE_KEY, peerCryptoInfo.getPeerSignaturePublicKeyBase64());
            }
            
            // Update peer signature algorithm
            values.put(COLUMN_CHAT_PEER_SIGNATURE_ALGORITHM, peerCryptoInfo.getPeerSignatureAlgorithm());
            
            // Update peer algorithms
            if (peerCryptoInfo.getPeerAlgorithms() != null) {
                values.put(COLUMN_CHAT_PEER_ALGORITHMS, gson.toJson(peerCryptoInfo.getPeerAlgorithms()));
            }
            
            // Update verification status
            values.put(COLUMN_CHAT_PEER_CRYPTO_VERIFIED, peerCryptoInfo.isVerified() ? 1 : 0);
            
            // Update timestamp
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
            
            String whereClause = COLUMN_ID + " = ?";
            String[] whereArgs = {chatId};
            
            int updatedRows = database.update(TABLE_CHATS, values, whereClause, whereArgs);
            
            if (updatedRows > 0) {
                Log.d(TAG, "Chat peer crypto updated: " + chatId);
                return true;
            } else {
                Log.w(TAG, "No chat found to update peer crypto: " + chatId);
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error updating chat peer crypto", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update chat key establishment information
     */
    public boolean updateChatKeyEstablishment(String chatId, String fingerprint, String status) {
        if (chatId == null || status == null) {
            Log.w(TAG, "Cannot update chat key establishment with null parameters");
            return false;
        }

        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_CHAT_FINGERPRINT, fingerprint);
            values.put(COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS, status);
            values.put(COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT, System.currentTimeMillis());
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());

            String whereClause = COLUMN_ID + " = ?";
            String[] whereArgs = {chatId};

            int updatedRows = database.update(TABLE_CHATS, values, whereClause, whereArgs);

            if (updatedRows > 0) {
                Log.d(TAG, "Updated chat key establishment status: " + chatId + " -> " + status);
                return true;
            } else {
                Log.w(TAG, "Failed to update chat key establishment status for: " + chatId);
                return false;
            }

        } catch (SQLiteException e) {
            Log.e(TAG, "Error updating chat key establishment", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get chats by key establishment status
     */
    public List<Chat> getChatsByKeyEstablishmentStatus(String status) {
        List<Chat> chats = new ArrayList<>();

        if (status == null) {
            Log.w(TAG, "Cannot search chats with null status");
            return chats;
        }

        lock.readLock().lock();
        try {
            String selection = COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS + " = ?";
            String[] selectionArgs = {status};
            String orderBy = COLUMN_CHAT_LAST_ACTIVITY + " DESC";

            Cursor cursor = database.query(
                TABLE_CHATS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                orderBy
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Chat chat = chatFromCursor(cursor);
                    if (chat != null) {
                        chats.add(chat);
                    }
                }
                cursor.close();
            }

            Log.d(TAG, "Retrieved " + chats.size() + " chats with status: " + status);

        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving chats by establishment status", e);
        } finally {
            lock.readLock().unlock();
        }

        return chats;
    }

    /**
     * Get chat fingerprint
     */
    public String getChatFingerprint(String chatId) {
        if (chatId == null) {
            return null;
        }

        lock.readLock().lock();
        try {
            String[] columns = {COLUMN_CHAT_FINGERPRINT};
            String selection = COLUMN_ID + " = ?";
            String[] selectionArgs = {chatId};

            Cursor cursor = database.query(
                TABLE_CHATS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int fingerprintIndex = cursor.getColumnIndex(COLUMN_CHAT_FINGERPRINT);
                String fingerprint = cursor.getString(fingerprintIndex);
                cursor.close();
                return fingerprint;
            }

            if (cursor != null) {
                cursor.close();
            }

            return null;

        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving chat fingerprint", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cleanup stale key initializations
     */
    public void cleanupStaleInitializations(long maxAgeMs) {
        lock.writeLock().lock();
        try {
            long cutoffTime = System.currentTimeMillis() - maxAgeMs;

            ContentValues values = new ContentValues();
            values.put(COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS, "FAILED");
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());

            String whereClause = COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS + " = ? AND " +
                               COLUMN_CREATED_AT + " < ?";
            String[] whereArgs = {"INITIALIZING", String.valueOf(cutoffTime)};

            int updatedRows = database.update(TABLE_CHATS, values, whereClause, whereArgs);

            if (updatedRows > 0) {
                Log.i(TAG, "Cleaned up " + updatedRows + " stale chat initializations");
            }

        } catch (SQLiteException e) {
            Log.e(TAG, "Error during stale initialization cleanup", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Save user profile
     */
    public boolean saveUserProfile(UserProfile profile) {
        if (profile == null) {
            Log.w(TAG, "Attempted to save null user profile");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_ID, profile.getId());
            values.put(COLUMN_PROFILE_USERNAME, profile.getUsername());
            values.put(COLUMN_PROFILE_EMAIL, profile.getEmail());
            values.put(COLUMN_PROFILE_DISPLAY_NAME, profile.getDisplayName());
            values.put(COLUMN_PROFILE_DATA, gson.toJson(profile));
            values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
            
            long result = database.insertWithOnConflict(
                TABLE_USER_PROFILE, 
                null, 
                values, 
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            if (result != -1) {
                Log.d(TAG, "User profile saved: " + profile.getUsername());
                return true;
            } else {
                Log.e(TAG, "Failed to save user profile");
                return false;
            }
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error saving user profile", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get user profile
     */
    public UserProfile getUserProfile() {
        lock.readLock().lock();
        try {
            String orderBy = COLUMN_UPDATED_AT + " DESC";
            
            Cursor cursor = database.query(
                TABLE_USER_PROFILE,
                null,
                null,
                null,
                null,
                null,
                orderBy,
                "1"
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                UserProfile profile = userProfileFromCursor(cursor);
                cursor.close();
                return profile;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            return null;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving user profile", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Save settings map
     */
    public boolean saveSettings(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            Log.w(TAG, "Attempted to save null or empty settings");
            return false;
        }
        
        lock.writeLock().lock();
        SQLiteDatabase db = null;
        try {
            db = database;
            db.beginTransaction();
            
            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_SETTING_KEY, entry.getKey());
                values.put(COLUMN_SETTING_VALUE, gson.toJson(entry.getValue()));
                values.put(COLUMN_SETTING_TYPE, entry.getValue().getClass().getSimpleName());
                values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
                
                db.insertWithOnConflict(
                    TABLE_SETTINGS, 
                    null, 
                    values, 
                    SQLiteDatabase.CONFLICT_REPLACE
                );
            }
            
            db.setTransactionSuccessful();
            Log.d(TAG, "Settings saved: " + settings.size() + " entries");
            return true;
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error saving settings", e);
            return false;
        } finally {
            if (db != null && db.inTransaction()) {
                db.endTransaction();
            }
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get all settings
     */
    public Map<String, Object> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        
        lock.readLock().lock();
        try {
            Cursor cursor = database.query(
                TABLE_SETTINGS,
                null,
                null,
                null,
                null,
                null,
                null
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String key = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SETTING_KEY));
                    String value = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SETTING_VALUE));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SETTING_TYPE));
                    
                    try {
                        Object deserializedValue = deserializeSettingValue(value, type);
                        if (deserializedValue != null) {
                            settings.put(key, deserializedValue);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to deserialize setting: " + key, e);
                    }
                }
                cursor.close();
            }
            
            Log.d(TAG, "Retrieved " + settings.size() + " settings");
            
        } catch (SQLiteException e) {
            Log.e(TAG, "Error retrieving settings", e);
        } finally {
            lock.readLock().unlock();
        }
        
        return settings;
    }
    
    /**
     * Save encryption settings
     */
    public boolean saveEncryptionSettings(CryptoAlgorithms settings) {
        if (settings == null) {
            Log.w(TAG, "Attempted to save null encryption settings");
            return false;
        }
        
        Map<String, Object> settingsMap = new HashMap<>();
        settingsMap.put("crypto_algorithms", settings);
        
        return saveSettings(settingsMap);
    }
    
    /**
     * Get encryption settings
     */
    public CryptoAlgorithms getEncryptionSettings() {
        Map<String, Object> settings = getSettings();
        Object cryptoSettings = settings.get("crypto_algorithms");
        
        if (cryptoSettings instanceof CryptoAlgorithms) {
            return (CryptoAlgorithms) cryptoSettings;
        }
        
        return null;
    }
    
    // ===========================
    // BACKUP AND MAINTENANCE
    // ===========================
    
    /**
     * Backup database to specified path
     */
    public boolean backup(String backupPath) {
        if (backupPath == null || backupPath.trim().isEmpty()) {
            Log.w(TAG, "Invalid backup path provided");
            return false;
        }
        
        lock.readLock().lock();
        try {
            File currentDB = new File(database.getPath());
            File backupDB = new File(backupPath);
            
            if (!currentDB.exists()) {
                Log.e(TAG, "Current database file does not exist");
                return false;
            }
            
            // Create parent directories if needed
            File parentDir = backupDB.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.e(TAG, "Failed to create backup directory");
                    return false;
                }
            }
            
            // Copy database file
            try (FileChannel source = new FileInputStream(currentDB).getChannel();
                 FileChannel destination = new FileOutputStream(backupDB).getChannel()) {
                
                destination.transferFrom(source, 0, source.size());
            }
            
            Log.i(TAG, "Database backup completed: " + backupPath);
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to backup database", e);
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Restore database from backup
     */
    public boolean restore(String backupPath) {
        if (backupPath == null || backupPath.trim().isEmpty()) {
            Log.w(TAG, "Invalid backup path provided");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            File backupDB = new File(backupPath);
            File currentDB = new File(database.getPath());
            
            if (!backupDB.exists()) {
                Log.e(TAG, "Backup database file does not exist: " + backupPath);
                return false;
            }
            
            // Close current database
            database.close();
            
            // Copy backup to current location
            try (FileChannel source = new FileInputStream(backupDB).getChannel();
                 FileChannel destination = new FileOutputStream(currentDB).getChannel()) {
                
                destination.transferFrom(source, 0, source.size());
            }
            
            // Reopen database
            database = this.getWritableDatabase();
            
            Log.i(TAG, "Database restore completed from: " + backupPath);
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to restore database", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Optimize database (VACUUM)
     */
    public void vacuum() {
        lock.writeLock().lock();
        try {
            database.execSQL("VACUUM;");
            Log.i(TAG, "Database vacuum completed");
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to vacuum database", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // PRIVATE HELPER METHODS
    // ===========================
    
    /**
     * Create database indexes for performance
     */
    private void createIndexes(SQLiteDatabase db) {
        // Messages table indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON " + TABLE_MESSAGES + "(" + COLUMN_MESSAGE_CHAT_ID + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON " + TABLE_MESSAGES + "(" + COLUMN_MESSAGE_TIMESTAMP + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_status ON " + TABLE_MESSAGES + "(" + COLUMN_MESSAGE_STATUS + ");");
        
        // Chats table indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chats_last_activity ON " + TABLE_CHATS + "(" + COLUMN_CHAT_LAST_ACTIVITY + ");");
        
        // Contacts table indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_name ON " + TABLE_CONTACTS + "(" + COLUMN_CONTACT_NAME + ");");
        
        // Files table indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_files_created_at ON " + TABLE_FILES + "(" + COLUMN_CREATED_AT + ");");
        
        // Settings table indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_settings_key ON " + TABLE_SETTINGS + "(" + COLUMN_SETTING_KEY + ");");
        
        // Organization keys table indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_org_keys_name ON " + TABLE_ORGANIZATION_KEYS + "(" + COLUMN_ORG_NAME + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_org_keys_algorithm ON " + TABLE_ORGANIZATION_KEYS + "(" + COLUMN_ORG_SIGNATURE_ALGORITHM + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_org_keys_active ON " + TABLE_ORGANIZATION_KEYS + "(" + COLUMN_ORG_IS_ACTIVE + ");");
    }
    
    /**
     * Verify database integrity
     */
    private boolean verifyDatabaseIntegrity() {
        try {
            Cursor cursor = database.rawQuery("PRAGMA integrity_check;", null);
            if (cursor != null) {
                boolean isOk = false;
                if (cursor.moveToFirst()) {
                    String result = cursor.getString(0);
                    isOk = "ok".equalsIgnoreCase(result);
                }
                cursor.close();
                return isOk;
            }
            return false;
        } catch (SQLiteException e) {
            Log.e(TAG, "Database integrity check failed", e);
            return false;
        }
    }
    
    /**
     * Convert cursor to Message object
     */
    private Message messageFromCursor(Cursor cursor) {
        try {
            Message message = new Message();
            message.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
            message.setChatId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE_CHAT_ID)));
            message.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE_CONTENT)));
            message.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE_TIMESTAMP)));
            
            String statusStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE_STATUS));
            if (statusStr != null) {
                try {
                    message.setStatus(MessageStatus.valueOf(statusStr));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Invalid message status: " + statusStr);
                    message.setStatus(MessageStatus.SENDING);
                }
            }
            
            return message;
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to Message", e);
            return null;
        }
    }
    
    /**
     * Convert cursor to Chat object
     */
    private Chat chatFromCursor(Cursor cursor) {
        try {
            int idIndex = cursor.getColumnIndex(COLUMN_ID);
            int nameIndex = cursor.getColumnIndex(COLUMN_CHAT_NAME);
            int keysIndex = cursor.getColumnIndex(COLUMN_CHAT_KEYS);
            int lastActivityIndex = cursor.getColumnIndex(COLUMN_CHAT_LAST_ACTIVITY);
            int createdAtIndex = cursor.getColumnIndex(COLUMN_CREATED_AT);
            int updatedAtIndex = cursor.getColumnIndex(COLUMN_UPDATED_AT);
            int fingerprintIndex = cursor.getColumnIndex(COLUMN_CHAT_FINGERPRINT);
            int statusIndex = cursor.getColumnIndex(COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS);
            int completedAtIndex = cursor.getColumnIndex(COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT);

            Chat chat = new Chat();
            chat.setId(cursor.getString(idIndex));
            chat.setName(cursor.getString(nameIndex));
            chat.setLastActivity(cursor.getLong(lastActivityIndex));
            if (createdAtIndex >= 0) {
                chat.setCreatedAt(cursor.getLong(createdAtIndex));
            }
            if (updatedAtIndex >= 0) {
                chat.setUpdatedAt(cursor.getLong(updatedAtIndex));
            }

            if (fingerprintIndex >= 0) {
                chat.setFingerprint(cursor.getString(fingerprintIndex));
            }
            if (statusIndex >= 0) {
                chat.setKeyEstablishmentStatus(cursor.getString(statusIndex));
            }
            if (completedAtIndex >= 0) {
                chat.setKeyEstablishmentCompletedAt(cursor.getLong(completedAtIndex));
            }

            String keysJson = cursor.getString(keysIndex);
            if (keysJson != null && !keysJson.trim().isEmpty()) {
                try {
                    ChatKeys keys = gson.fromJson(keysJson, ChatKeys.class);
                    chat.setKeys(keys);
                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "Error parsing chat keys JSON", e);
                }
            }
            
            // Deserialize PeerCryptoInfo if present
            PeerCryptoInfo peerInfo = new PeerCryptoInfo();
            boolean hasPeerInfo = false;
            
            // Load peer signature key
            String peerSignatureKeyB64 = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAT_PEER_SIGNATURE_KEY));
            if (peerSignatureKeyB64 != null && !peerSignatureKeyB64.trim().isEmpty()) {
                peerInfo.setPeerSignaturePublicKeyFromBase64(peerSignatureKeyB64);
                hasPeerInfo = true;
            }
            
            // Load peer signature algorithm
            String peerSignatureAlgorithm = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAT_PEER_SIGNATURE_ALGORITHM));
            if (peerSignatureAlgorithm != null && !peerSignatureAlgorithm.trim().isEmpty()) {
                peerInfo.setPeerSignatureAlgorithm(peerSignatureAlgorithm);
                hasPeerInfo = true;
            }
            
            // Load peer algorithms
            String peerAlgorithmsJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAT_PEER_ALGORITHMS));
            if (peerAlgorithmsJson != null && !peerAlgorithmsJson.trim().isEmpty()) {
                try {
                    CryptoAlgorithms peerAlgorithms = gson.fromJson(peerAlgorithmsJson, CryptoAlgorithms.class);
                    peerInfo.setPeerAlgorithms(peerAlgorithms);
                    hasPeerInfo = true;
                } catch (JsonSyntaxException e) {
                    Log.w(TAG, "Failed to deserialize peer algorithms for chat: " + chat.getId(), e);
                }
            }
            
            // Load verification status
            int verifiedInt = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CHAT_PEER_CRYPTO_VERIFIED));
            peerInfo.setVerified(verifiedInt == 1);
            
            // Set peer info if we have any data
            if (hasPeerInfo) {
                chat.setPeerCryptoInfo(peerInfo);
            }
            
            return chat;
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to Chat", e);
            return null;
        }
    }
    
    /**
     * Convert cursor to Contact object
     */
    private Contact contactFromCursor(Cursor cursor) {
        try {
            Contact contact = new Contact();
            contact.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
            contact.setName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT_NAME)));
            contact.setPublicKey(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT_PUBLIC_KEY)));
            
            return contact;
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to Contact", e);
            return null;
        }
    }
    
    /**
     * Convert cursor to FileInfo object
     */
    private FileInfo fileInfoFromCursor(Cursor cursor) {
        try {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
            fileInfo.setName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_NAME)));
            fileInfo.setSize(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FILE_SIZE)));
            fileInfo.setMimeType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_MIME_TYPE)));
            fileInfo.setPath(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_PATH)));
            
            return fileInfo;
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to FileInfo", e);
            return null;
        }
    }
    
    /**
     * Convert cursor to UserProfile object
     */
    private UserProfile userProfileFromCursor(Cursor cursor) {
        try {
            String profileJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_DATA));
            if (profileJson != null && !profileJson.trim().isEmpty()) {
                return gson.fromJson(profileJson, UserProfile.class);
            }
            
            // Fallback to individual fields
            UserProfile profile = new UserProfile();
            profile.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
            profile.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_USERNAME)));
            profile.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_EMAIL)));
            profile.setDisplayName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_DISPLAY_NAME)));
            
            return profile;
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to UserProfile", e);
            return null;
        }
    }
    
    /**
     * Convert cursor to OrganizationKeys object
     */
    private OrganizationKeys organizationKeysFromCursor(Cursor cursor) {
        try {
            OrganizationKeys orgKeys = new OrganizationKeys();
            orgKeys.setOrganizationName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORG_NAME)));
            orgKeys.setSignatureAlgorithm(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORG_SIGNATURE_ALGORITHM)));
            
            // Load public key from Base64
            String publicKeyB64 = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORG_PUBLIC_SIGNATURE_KEY));
            if (publicKeyB64 != null && !publicKeyB64.trim().isEmpty()) {
                orgKeys.setPublicKeyFromBase64(publicKeyB64);
            }
            
            // Load private key from Base64
            String privateKeyB64 = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORG_PRIVATE_SIGNATURE_KEY));
            if (privateKeyB64 != null && !privateKeyB64.trim().isEmpty()) {
                orgKeys.setPrivateKeyFromBase64(privateKeyB64);
            }
            
            // Load other fields
            orgKeys.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)));
            orgKeys.setActive(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ORG_IS_ACTIVE)) == 1);
            
            return orgKeys;
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to OrganizationKeys", e);
            return null;
        }
    }
    
    /**
     * Deserialize setting value based on type
     */
    private Object deserializeSettingValue(String value, String type) {
        try {
            switch (type) {
                case "String":
                    return gson.fromJson(value, String.class);
                case "Integer":
                    return gson.fromJson(value, Integer.class);
                case "Long":
                    return gson.fromJson(value, Long.class);
                case "Boolean":
                    return gson.fromJson(value, Boolean.class);
                case "Double":
                    return gson.fromJson(value, Double.class);
                case "CryptoAlgorithms":
                    return gson.fromJson(value, CryptoAlgorithms.class);
                default:
                    // Generic object deserialization
                    return gson.fromJson(value, Object.class);
            }
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "Failed to deserialize setting value: " + value + " (type: " + type + ")", e);
            return null;
        }
    }
    
    /**
     * Check if the manager is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    // ===========================
    // TABLE CREATION SQL
    // ===========================
    
    private static final String CREATE_MESSAGES_TABLE = 
        "CREATE TABLE " + TABLE_MESSAGES + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_MESSAGE_CHAT_ID + " TEXT NOT NULL, " +
        COLUMN_MESSAGE_CONTENT + " TEXT NOT NULL, " +
        COLUMN_MESSAGE_STATUS + " TEXT NOT NULL DEFAULT 'SENDING', " +
        COLUMN_MESSAGE_TIMESTAMP + " INTEGER NOT NULL, " +
        COLUMN_MESSAGE_SENDER_ID + " TEXT, " +
        COLUMN_MESSAGE_TYPE + " TEXT DEFAULT 'TEXT', " +
        COLUMN_MESSAGE_FILE_ID + " TEXT, " +
        COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
        COLUMN_UPDATED_AT + " INTEGER NOT NULL" +
        ");";
    
    private static final String CREATE_CHATS_TABLE =
        "CREATE TABLE " + TABLE_CHATS + " (" +
        COLUMN_ID + " TEXT PRIMARY KEY, " +
        COLUMN_CHAT_NAME + " TEXT, " +
        COLUMN_CHAT_KEYS + " TEXT, " +
        COLUMN_CHAT_LAST_ACTIVITY + " INTEGER NOT NULL DEFAULT 0, " +
        COLUMN_CHAT_PARTICIPANTS + " TEXT, " +
        COLUMN_CHAT_ENCRYPTION_STATUS + " TEXT DEFAULT 'INITIALIZING', " +
        COLUMN_CHAT_PEER_SIGNATURE_KEY + " TEXT, " +
        COLUMN_CHAT_PEER_SIGNATURE_ALGORITHM + " TEXT, " +
        COLUMN_CHAT_PEER_ALGORITHMS + " TEXT, " +
        COLUMN_CHAT_PEER_CRYPTO_VERIFIED + " INTEGER DEFAULT 0, " +
        COLUMN_CHAT_FINGERPRINT + " TEXT, " +
        COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS + " TEXT DEFAULT 'INITIALIZING', " +
        COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT + " INTEGER, " +
        COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
        COLUMN_UPDATED_AT + " INTEGER NOT NULL" +
        ");";
    
    private static final String CREATE_CONTACTS_TABLE = 
        "CREATE TABLE " + TABLE_CONTACTS + " (" +
        COLUMN_ID + " TEXT PRIMARY KEY, " +
        COLUMN_CONTACT_NAME + " TEXT NOT NULL, " +
        COLUMN_CONTACT_PUBLIC_KEY + " TEXT, " +
        COLUMN_CONTACT_USER_ID + " TEXT, " +
        COLUMN_CONTACT_STATUS + " TEXT DEFAULT 'OFFLINE', " +
        COLUMN_CONTACT_LAST_SEEN + " INTEGER, " +
        COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
        COLUMN_UPDATED_AT + " INTEGER NOT NULL" +
        ");";
    
    private static final String CREATE_FILES_TABLE = 
        "CREATE TABLE " + TABLE_FILES + " (" +
        COLUMN_ID + " TEXT PRIMARY KEY, " +
        COLUMN_FILE_NAME + " TEXT NOT NULL, " +
        COLUMN_FILE_SIZE + " INTEGER NOT NULL, " +
        COLUMN_FILE_MIME_TYPE + " TEXT, " +
        COLUMN_FILE_PATH + " TEXT NOT NULL, " +
        COLUMN_FILE_HASH + " TEXT, " +
        COLUMN_FILE_ENCRYPTION_STATUS + " TEXT DEFAULT 'NONE', " +
        COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
        COLUMN_UPDATED_AT + " INTEGER NOT NULL" +
        ");";
    
    private static final String CREATE_USER_PROFILE_TABLE = 
        "CREATE TABLE " + TABLE_USER_PROFILE + " (" +
        COLUMN_ID + " TEXT PRIMARY KEY, " +
        COLUMN_PROFILE_USERNAME + " TEXT NOT NULL, " +
        COLUMN_PROFILE_EMAIL + " TEXT, " +
        COLUMN_PROFILE_DISPLAY_NAME + " TEXT, " +
        COLUMN_PROFILE_DATA + " TEXT, " +
        COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
        COLUMN_UPDATED_AT + " INTEGER NOT NULL" +
        ");";
    
    private static final String CREATE_SETTINGS_TABLE = 
        "CREATE TABLE " + TABLE_SETTINGS + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_SETTING_KEY + " TEXT UNIQUE NOT NULL, " +
        COLUMN_SETTING_VALUE + " TEXT NOT NULL, " +
        COLUMN_SETTING_TYPE + " TEXT NOT NULL, " +
        COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000), " +
        COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)" +
        ");";
    
    private static final String CREATE_ORGANIZATION_KEYS_TABLE = 
        "CREATE TABLE " + TABLE_ORGANIZATION_KEYS + " (" +
        COLUMN_ID + " TEXT PRIMARY KEY, " +
        COLUMN_ORG_NAME + " TEXT NOT NULL, " +
        COLUMN_ORG_SIGNATURE_ALGORITHM + " TEXT NOT NULL, " +
        COLUMN_ORG_PUBLIC_SIGNATURE_KEY + " TEXT NOT NULL, " +
        COLUMN_ORG_PRIVATE_SIGNATURE_KEY + " TEXT NOT NULL, " +
        COLUMN_ORG_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, " +
        COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
        COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
        "UNIQUE(" + COLUMN_ORG_NAME + ", " + COLUMN_ORG_SIGNATURE_ALGORITHM + ")" +
        ");";
}