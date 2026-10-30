package com.kuet.krishichain.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NetworkDao {

    @Insert
    void insertNodes(List<NodeEntity> nodes);

    @Insert
    void insertEdges(List<EdgeEntity> edges);

    @Insert
    void insertPrices(List<PriceEntity> prices);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWeather(List<WeatherEntity> weather);

    @Query("DELETE FROM nodes")
    void clearNodes();

    @Query("DELETE FROM edges")
    void clearEdges();

    @Query("DELETE FROM prices")
    void clearPrices();

    @Query("DELETE FROM weather")
    void clearWeather();

    @Query("SELECT COUNT(*) FROM nodes")
    int nodeCount();

    @Query("SELECT COUNT(*) FROM edges")
    int edgeCount();

    @Query("SELECT COUNT(*) FROM prices")
    int priceCount();

    @Query("SELECT COUNT(*) FROM prices WHERE source = 'DUMMY'")
    int dummyPriceCount();

    /** Places a farmer can start from. */
    @Query("SELECT * FROM nodes WHERE type IN ('village', 'hub') ORDER BY name")
    List<NodeEntity> getOrigins();

    @Query("SELECT * FROM nodes ORDER BY id")
    List<NodeEntity> getAllNodes();

    @Query("SELECT * FROM edges")
    List<EdgeEntity> getAllEdges();

    @Query("SELECT * FROM prices WHERE crop = :crop")
    List<PriceEntity> getPricesForCrop(String crop);

    @Query("SELECT * FROM weather")
    List<WeatherEntity> getWeather();

    @Query("UPDATE prices SET price = :price, source = :source, date = :date WHERE nodeId = :nodeId AND crop = :crop")
    int updatePrice(int nodeId, String crop, double price, String source, String date);

    @Query("SELECT * FROM prices WHERE nodeId = :nodeId AND crop = :crop LIMIT 1")
    PriceEntity getPrice(int nodeId, String crop);
}
