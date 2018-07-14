package fooddesertserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fooddesertdatabase.FoodDesertDatabase;
import grocerystoresource.GooglePlacesClient;
import grocerystoresource.GroceryStoreSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import spark.Request;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static spark.Spark.get;
import static spark.Spark.staticFiles;

public class FoodDesertServer {

    private static void printUsage() {
        System.out.println("Usage: java -jar FoodDesertServer.jar database_file [google_api_key]");
        System.out.println("\tdatabase_file: SqLite database file containing tables created by this server.");
        System.out.println("\tgoogle_api_key: a valid key for the Google Places API. If omitted, this\n\t\tprogram will look for a Java properties file containing a key value pair:");
        System.out.println("\t\tgoogle_api_key=$YOUR_API_KEY");
    }

    /**
     * Provides parsing for the common format for search envelopes in get requests.
     *
     *  i.e. ?lng0=0&lng1=0&lat0=0&lat1=0
     *
     * @param request GET request containing double query params lng0, lng1, lat0, lat1.
     * @return Envelope constructed from query params parsed as doubles.
     */
    private static Envelope parseRequestEnvelope(Request request){
        double lng0 = Double.parseDouble(request.queryParams("lng0"));
        double lng1 = Double.parseDouble(request.queryParams("lng1"));
        double lat0 = Double.parseDouble(request.queryParams("lat0"));
        double lat1 = Double.parseDouble(request.queryParams("lat1"));

        return new Envelope(lng0, lng1, lat0, lat1);
    }

    private static Gson buildGson(){
        GsonBuilder builder = new GsonBuilder();

        /*custom serialization to rename fields of the Coordinate field*/
        builder.registerTypeAdapter(GroceryStore.class, new GroceryStore.JsonSerializer());

        /*custom serialization for VoronoiDiagrams and their underlying GeometryCollection */
        builder.registerTypeAdapter(VoronoiDiagram.class, new VoronoiDiagram.JsonSerializer());

        return builder.create();
    }

    private static void setupRoutes(FoodDesertQueryHandler queryHandler) {
        Gson gson = buildGson();

        staticFiles.location("/public");

        get("/is_in_food_desert", (request, response) -> {
            double lng = Double.parseDouble(request.queryParams("lng"));
            double lat = Double.parseDouble(request.queryParams("lat"));
            Coordinate location = new Coordinate(lng,lat);

            boolean isInFoodDesert = queryHandler.isInFoodDesert(location);

            return gson.toJson(isInFoodDesert);
        });

        get("/locate_stores", (request, response) -> {
            Envelope queryArea = parseRequestEnvelope(request);

            List<GroceryStore> result = queryHandler.getAllGroceryStore(queryArea);

            return gson.toJson(result);
        });

        get("/voronoi_stores", (request, response) -> {
            Envelope queryArea = parseRequestEnvelope(request);

            VoronoiDiagram result = queryHandler.getVoronoiDiagram(queryArea);

            return gson.toJson(result);
        });
    }

    public static void main(String[] args) throws IOException, SQLException {
        if(args.length < 1) {
            printUsage();
            return;
        }

        String dbFile = args[0];

        /*get google API key from either arguments or file*/
        String googleApiKey;
        if(args.length < 2) {
            if (Files.exists(Paths.get("google_api_key"), LinkOption.NOFOLLOW_LINKS)) {
                 Properties props = new Properties();
                 try (FileInputStream in = new FileInputStream("google_api_key")) {
                     props.load(in);
                 }
                 googleApiKey = props.getProperty("google_api_key");
            } else {
                printUsage();
                return;
            }
        } else {
            googleApiKey = args[1];
        }

        /*load existing database or create a new one as needed */
        FoodDesertDatabase database;
        Path dbPath = Paths.get(dbFile);
        if (Files.exists(dbPath, LinkOption.NOFOLLOW_LINKS)) {
            database = new FoodDesertDatabase(dbFile);
        } else {
            database = FoodDesertDatabase.createDatabase(dbFile);
        }

        GroceryStoreSource client = new GooglePlacesClient(googleApiKey);
        FoodDesertQueryHandler queryHandler = new FoodDesertQueryHandler(database, client);

        setupRoutes(queryHandler);
    }
}
