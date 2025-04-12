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
            System.out.println(ANSI_CYAN + "\n███████╗██╗   ██╗███╗   ███╗██████╗ ███████╗██╗ █████╗  █████╗ ███████╗██████╗ ");
            System.out.println("██╔════╝██║   ██║████╗ ████║██╔══██╗██╔════╝██║██╔══██╗██╔══██╗██╔════╝██╔══██╗");
            System.out.println("█████╗  ██║   ██║██╔████╔██║██████╔╝█████╗  ██║███████║██║  ╚═╝█████╗  ██████╔╝");
            System.out.println("██╔══╝  ██║   ██║██║╚██╔╝██║██╔═══╝ ██╔══╝  ██║██╔══██║██║  ██╗██╔══╝  ██╔══██╗");
            System.out.println("██║     ╚██████╔╝██║ ╚═╝ ██║██║     ███████╗██║██║  ██║╚█████╔╝███████╗██║  ██║");
            System.out.println("╚═╝      ╚═════╝ ╚═╝     ╚═╝╚═╝     ╚══════╝╚═╝╚═╝  ╚═╝ ╚════╝ ╚══════╝╚═╝  ╚═╝" + ANSI_RESET);
            System.out.println(ANSI_PURPLE + "\n--- Media Tracker Menu ---" + ANSI_RESET);
            System.out.println("1. Add Media Entry");
            System.out.println("2. Update Media Entry");
            System.out.println("3. Delete Media Entry");
            System.out.println("4. View Daily Report");
            System.out.println("5. View Weekly Report");
            System.out.println("6. View Most Watched Genre");
            System.out.println("7. Exit");
            System.out.print(ANSI_BLUE + "Choose an option: " + ANSI_RESET);

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
                default -> System.out.println(ANSI_RED + "Invalid option. Try again." + ANSI_RESET);
            }
        }
    }

    private static void createDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            String sql = """
            CREATE TABLE IF NOT EXISTS media (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                genre TEXT,
                runtime INTEGER,
                type TEXT,
                date_watched TEXT
            )
        """;

            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(ANSI_RED + "Database creation failed: " + e.getMessage() + ANSI_RESET);
        }
    }

    private static void addMedia(Scanner scanner) {
        System.out.print("Enter movie or TV show title: ");
        String title = scanner.nextLine();

        List<JSONObject> options = searchMediaOptions(title);
        if (options.isEmpty()) {
            System.out.println(ANSI_RED + "No results found. Try again." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_GREEN + "\nSearch Results:" + ANSI_RESET);
        for (int i = 0; i < options.size(); i++) {
            JSONObject item = options.get(i);
            String name = item.optString("title", item.optString("name", "Unknown"));
            String type = item.getString("media_type");
            String releaseDate = item.optString("release_date", item.optString("first_air_date", "Unknown"));
            String posterPath = item.optString("poster_path", "");
            String posterUrl = posterPath.isEmpty() ? "N/A" : "https://image.tmdb.org/t/p/w500" + posterPath;

            System.out.printf(ANSI_CYAN + "%d. [%s] %s (%s)\n" + ANSI_RESET, i + 1, type.toUpperCase(), name, releaseDate);
            System.out.println("   Poster: " + posterUrl);
        }

        System.out.print(ANSI_BLUE + "Select the correct option by number: " + ANSI_RESET);
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        if (choice < 1 || choice > options.size()) {
            System.out.println(ANSI_RED + "Invalid selection." + ANSI_RESET);
            return;
        }

        JSONObject selected = options.get(choice - 1);
        String type = selected.getString("media_type");
        int tmdbId = selected.getInt("id");

        JSONObject details = fetchDetailsById(tmdbId, type);
        if (details == null) {
            System.out.println(ANSI_RED + "Failed to fetch full metadata." + ANSI_RESET);
            return;
        }

        String name = details.optString("title", details.optString("name", "Unknown"));
        String genre = details.optJSONArray("genres").optJSONObject(0).optString("name", "Unknown");
        int runtime = details.has("runtime") ? details.optInt("runtime") : details.optJSONArray("episode_run_time").optInt(0, 0);

        LocalDate dateWatched = LocalDate.now();

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT INTO media (title, genre, runtime, type, date_watched) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);
            pstmt.setString(2, genre);
            pstmt.setInt(3, runtime);
            pstmt.setString(4, type.toUpperCase());
            pstmt.setString(5, dateWatched.toString());
            pstmt.executeUpdate();

            System.out.println(ANSI_GREEN + "Media saved successfully!" + ANSI_RESET);
        } catch (SQLException e) {
            System.out.println(ANSI_RED + "Error saving media: " + e.getMessage() + ANSI_RESET);
        }
    }

    private static List<JSONObject> searchMediaOptions(String title) {
        List<JSONObject> results = new ArrayList<>();
        String url = "https://api.themoviedb.org/3/search/multi?query=" + title.replace(" ", "%20") + "&api_key=" + TMDB_API_KEY;

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseData = response.body().string();
                JSONObject json = new JSONObject(responseData);
                JSONArray array = json.getJSONArray("results");

                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String mediaType = item.optString("media_type", "");
                    if (mediaType.equals("movie") || mediaType.equals("tv")) {
                        results.add(item);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(ANSI_RED + "Error during TMDb search: " + e.getMessage() + ANSI_RESET);
        }

        return results;
    }


    private static JSONObject fetchDetailsById(int id, String type) {
        String url = String.format("https://api.themoviedb.org/3/%s/%d?api_key=%s", type, id, TMDB_API_KEY);

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return new JSONObject(response.body().string());
            }
        } catch (Exception e) {
            System.out.println(ANSI_RED + "Error fetching details: " + e.getMessage() + ANSI_RESET);
        }

        return null;
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
