import java.sql.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import okhttp3.*;
import org.json.*;

public class MediaTrackerApp {

    private static final String DB_URL = "jdbc:sqlite:media_tracker.db";
    private static final String TMDB_API_KEY = "139b3b69f6805fb68bd0d5ba0ad3a0fc";
    private static final OkHttpClient client = new OkHttpClient();

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";

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

    private static void createDatabase() {

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            String sql = "CREATE TABLE IF NOT EXISTS media (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT NOT NULL," +
                    "type TEXT NOT NULL," +
                    "genre TEXT," +
                    "duration_minutes INTEGER," +
                    "watched_date DATE" +
                    ");";
            stmt.execute(sql);

        } catch (SQLException e) {
            System.out.print("Database error: " + e.getMessage());
        }
    }

    private static void addMedia(Scanner scanner) {
        System.out.print("Title: ");
        String title = scanner.nextLine();
        System.out.print("Type (Movie/TV): ");
        String type = scanner.nextLine().toLowerCase();

        Map<String, Object> metadata = fetchMediaMetadata(title, type);
        if (metadata == null) {
            System.out.println("Could not fetch metadata. Entering manually.");
            System.out.print("Genre: ");
            metadata = new HashMap<>();
            metadata.put("genre", scanner.nextLine());
            System.out.print("Duration (minutes): ");
            metadata.put("duration", scanner.nextInt());
            scanner.nextLine();
        } else {
            System.out.println("Fetched metadata:");
            System.out.println("Title: " + title);
            System.out.println("Genre: " + metadata.get("genre"));
            System.out.println("Duration: " + metadata.get("duration") + " minutes");
            System.out.print("Do you want to save this? (yes/no): ");
            if (!scanner.nextLine().equalsIgnoreCase("yes")) {
                return;
            }
        }

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO media (title, type, genre, duration_minutes, watched_date) VALUES (?, ?, ?, ?, ?)")) {
            pstmt.setString(1, title);
            pstmt.setString(2, type);
            pstmt.setString(3, (String) metadata.get("genre"));
            pstmt.setInt(4, (Integer) metadata.get("duration"));
            pstmt.setString(5, LocalDate.now().toString());
            pstmt.executeUpdate();
            System.out.println("Media added.");
        } catch (SQLException e) {
            System.out.println("Insert error: " + e.getMessage());
        }
    }

    private static Map<String, Object> fetchMediaMetadata(String title, String type) {
        try {
            String encodedTitle = title.replace(" ", "%20");
            String url = "https://api.themoviedb.org/3/search/" + (type.equals("tv") ? "tv" : "movie") +
                    "?api_key=" + TMDB_API_KEY + "&query=" + encodedTitle;

            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) return null;

            JSONObject json = new JSONObject(response.body().string());
            JSONArray results = json.getJSONArray("results");

            if (results.length() == 0) return null;

            JSONObject media = results.getJSONObject(0);
            int id = media.getInt("id");

            String detailsUrl = "https://api.themoviedb.org/3/" + (type.equals("tv") ? "tv" : "movie") +
                    "/" + id + "?api_key=" + TMDB_API_KEY;
            Request detailsRequest = new Request.Builder().url(detailsUrl).build();
            Response detailsResponse = client.newCall(detailsRequest).execute();

            if (!detailsResponse.isSuccessful()) return null;

            JSONObject details = new JSONObject(detailsResponse.body().string());
            JSONArray genresArray = details.getJSONArray("genres");
            String genre = genresArray.length() > 0 ? genresArray.getJSONObject(0).getString("name") : "Unknown";

            int duration = type.equals("tv") ? details.optInt("episode_run_time", 30) : details.optInt("runtime", 90);
            if (type.equals("tv") && details.has("episode_run_time")) {
                JSONArray runTimes = details.getJSONArray("episode_run_time");
                if (runTimes.length() > 0) duration = runTimes.getInt(0);
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("genre", genre);
            metadata.put("duration", duration);
            return metadata;
        } catch (Exception e) {
            System.out.println("API error: " + e.getMessage());
            return null;
        }
    }

    private static void updateMedia(Scanner scanner) {
        System.out.print("Enter media ID to update: ");
        int id = scanner.nextInt();
        scanner.nextLine();
        System.out.print("New Title: ");
        String title = scanner.nextLine();
        System.out.print("New Type: ");
        String type = scanner.nextLine();
        System.out.print("New Genre: ");
        String genre = scanner.nextLine();
        System.out.print("New Duration (minutes): ");
        int duration = scanner.nextInt();
        scanner.nextLine();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE media SET title=?, type=?, genre=?, duration_minutes=? WHERE id=?")) {
            pstmt.setString(1, title);
            pstmt.setString(2, type);
            pstmt.setString(3, genre);
            pstmt.setInt(4, duration);
            pstmt.setInt(5, id);
            pstmt.executeUpdate();
            System.out.println("Media updated.");
        } catch (SQLException e) {
            System.out.println("Update error: " + e.getMessage());
        }
    }

    private static void deleteMedia(Scanner scanner) {
        System.out.print("Enter media ID to delete: ");
        int id = scanner.nextInt();
        scanner.nextLine();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM media WHERE id=?")) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            System.out.println("Media deleted.");
        } catch (SQLException e) {
            System.out.println("Delete error: " + e.getMessage());
        }
    }

    private static void viewReport(LocalDate date) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT SUM(duration_minutes) FROM media WHERE watched_date = ?")) {
            pstmt.setString(1, date.toString());
            ResultSet rs = pstmt.executeQuery();
            int total = rs.getInt(1);
            System.out.printf("Total watched on %s: %d hour(s) %d minute(s)%n", date, total / 60, total % 60);
        } catch (SQLException e) {
            System.out.println("Report error: " + e.getMessage());
        }
    }

    private static void viewWeeklyReport() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(6);

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT SUM(duration_minutes) FROM media WHERE watched_date BETWEEN ? AND ?")) {
            pstmt.setString(1, startOfWeek.toString());
            pstmt.setString(2, today.toString());
            ResultSet rs = pstmt.executeQuery();
            int total = rs.getInt(1);
            System.out.printf("Total watched from %s to %s: %d hour(s) %d minute(s)%n", startOfWeek, today, total / 60, total % 60);
        } catch (SQLException e) {
            System.out.println("Weekly report error: " + e.getMessage());
        }
    }

    private static void viewMostWatchedGenre() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String sql = "SELECT genre, COUNT(*) as count FROM media GROUP BY genre ORDER BY count DESC LIMIT 1";
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                System.out.printf("Most watched genre: %s (Watched %d times)%n", rs.getString("genre"), rs.getInt("count"));
            } else {
                System.out.println("No data available.");
            }
        } catch (SQLException e) {
            System.out.println("Genre report error: " + e.getMessage());
        }
    }
}
