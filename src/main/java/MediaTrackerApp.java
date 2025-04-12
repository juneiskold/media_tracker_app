import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import okhttp3.*;
import org.json.*;

public class MediaTrackerApp {

    private static final String DB_URL = "jdbc:sqlite:media_tracker.db";
    private static final String TMDB_API_KEY = "139b3b69f6805fb68bd0d5ba0ad3a0fc";
    private static final OkHttpClient client = new OkHttpClient();


    public static void main(String[] args) {
        createDatabase();
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.println("\n--- Media Tracker Menu ---");
            System.out.println("1. Add Media Entry");
            System.out.println("2. Update Media Entry");
            System.out.println("3. Delete Media Entry");
            System.out.println("4. View Daily Report");
            System.out.println("5. View Weekly Report");
            System.out.println("6. View Most Watched Genre");
            System.out.println("7. Exit");
            System.out.print("Choose an option: ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1 -> addMedia(scanner);
                case 2 -> updateMedia(scanner);
                case 3 -> deleteMedia(scanner);
                case 4 -> viewReport(LocalDate.now());
                case 5 -> viewWeeklyReport();
                case 6 -> viewMostWatchedGenre();
                case 7 -> running = false;
                default -> System.out.println("Invalid option. Try again.");
            }
        }
    }
}
