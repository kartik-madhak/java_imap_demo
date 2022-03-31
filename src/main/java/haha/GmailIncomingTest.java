package haha;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.util.Properties;

public class GmailIncomingTest {

  private static final String username = "mymail@gmail.com";
  private static final String password = "password";

  public static void main(String[] args) {

    Properties properties = new Properties();
    // properties.put("mail.debug", "true");
    properties.put("mail.store.protocol", "imaps");
    properties.put("mail.imaps.host", "imap.gmail.com");
    properties.put("mail.imaps.port", "993");
    properties.put("mail.imaps.timeout", "10000");

    Session session = Session.getInstance(properties); // not getDefaultInstance
    IMAPStore store = null;
    Folder inbox = null;

    try {
      store = (IMAPStore) session.getStore("imaps");
      store.connect(username, password);

      if (!store.hasCapability("IDLE")) {
        throw new RuntimeException("IDLE not supported");
      }

      inbox = store.getFolder("INBOX");
      inbox.addMessageCountListener(
          new MessageCountAdapter() {

            @Override
            public void messagesAdded(MessageCountEvent event) {
              Message[] messages = event.getMessages();

              for (Message message : messages) {
                try {
                  System.out.println("Mail Subject:- " + message.getSubject());
                  System.out.println("Mail Content:- " + getContentFromMessage(message));
                } catch (MessagingException | IOException e) {
                  e.printStackTrace();
                }
              }
            }
          });

      IdleThread idleThread = new IdleThread(inbox);
      idleThread.setDaemon(false);
      idleThread.start();

      idleThread.join();
      // idleThread.kill(); //to terminate from another thread

    } catch (Exception e) {
      e.printStackTrace();
    } finally {

      close(inbox);
      close(store);
    }
  }

  private static String getContentFromMessage(Message message)
      throws MessagingException, IOException {
    MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
    return getTextFromMimeMultipart(mimeMultipart);
  }

  public static void close(final Folder folder) {
    try {
      if (folder != null && folder.isOpen()) {
        folder.close(false);
      }
    } catch (final Exception e) {
      // ignore
    }
  }

  public static void close(final Store store) {
    try {
      if (store != null && store.isConnected()) {
        store.close();
      }
    } catch (final Exception e) {
      // ignore
    }
  }

  public static void ensureOpen(final Folder folder) throws MessagingException {

    if (folder != null) {
      Store store = folder.getStore();
      if (store != null && !store.isConnected()) {
        store.connect(username, password);
      }
    } else {
      throw new MessagingException("Unable to open a null folder");
    }

    if (folder.exists() && !folder.isOpen() && (folder.getType() & Folder.HOLDS_MESSAGES) != 0) {
      System.out.println("open folder " + folder.getFullName());
      folder.open(Folder.READ_ONLY);
      if (!folder.isOpen())
        throw new MessagingException("Unable to open folder " + folder.getFullName());
    }
  }

  private static String getTextFromMimeMultipart(MimeMultipart mimeMultipart)
      throws MessagingException, IOException {
    StringBuilder result = new StringBuilder();
    int count = mimeMultipart.getCount();
    for (int i = 0; i < count; i++) {
      BodyPart bodyPart = mimeMultipart.getBodyPart(i);
      if (bodyPart.isMimeType("text/plain")) {
        result.append("\n").append(bodyPart.getContent());
        break; // without break same text appears twice in my tests
      } else if (bodyPart.isMimeType("text/html")) {
        String html = (String) bodyPart.getContent();
        result.append("\n").append(org.jsoup.Jsoup.parse(html).text());
      } else if (bodyPart.getContent() instanceof MimeMultipart) {
        result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
      }
    }
    return result.toString();
  }

  private static class IdleThread extends Thread {
    private final Folder folder;
    private volatile boolean running = true;

    public IdleThread(Folder folder) {
      super();
      this.folder = folder;
    }

    public synchronized void kill() {

      if (!running) return;
      this.running = false;
    }

    @Override
    public void run() {
      while (running) {

        try {
          ensureOpen(folder);
          System.out.println("enter idle");
          ((IMAPFolder) folder).idle();
        } catch (Exception e) {
          // something went wrong
          // wait and try again
          e.printStackTrace();
          try {
            Thread.sleep(100);
          } catch (InterruptedException e1) {
            // ignore
          }
        }
      }
    }
  }
}
