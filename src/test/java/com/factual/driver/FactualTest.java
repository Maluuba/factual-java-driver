package com.factual.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;


/**
 * Integration tests for the Factual Java driver. Expects your key and secret to be in:
 * <pre>
 * src/test/resources/key.txt
 * src/test/resources/secret.txt
 * </pre>
 * 
 * @author aaron
 */
public class FactualTest {
  private static Factual factual;

  final double latitude = 34.06018;
  final double longitude = -118.41835;
  final int meters = 5000;

  @Before
  public void setup() {
    String key = read("key.txt");
    String secret = read("secret.txt");
    factual = new Factual(key, secret);
  }

  @Test
  public void testSchema() {
    SchemaResponse schema = factual.schema("restaurants-us");
    assertTrue(schema.getTitle().toLowerCase().contains("restaurant"));
    assertTrue(schema.isGeoEnabled());
    assertTrue(schema.isSearchEnabled());

    assertFalse(schema.getColumnSchemas().isEmpty());

    ColumnSchema nameSchema = schema.getColumnSchema("name");
    assertEquals("name", nameSchema.name);
    assertEquals("string", nameSchema.datatype);
  }
  
  /**
   * Find rows in the global places database in the United States
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
  public void testCoreExample1() {
    ReadResponse resp = factual.fetch("places",
        new Query().field("country").equal("US"));

    assertOk(resp);
    assertAll(resp, "country", "US");
    
    Map<String, Object> params = Maps.newHashMap();
    params.put("filters", JsonUtil.toJsonStr(
		new HashMap() {{  
			put("country", new HashMap() {{
				put("$eq", "US");	    
			}});
		}})
    );
    String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);
  }

  /**
   * Find rows in the restaurant database whose name begins with "Star" and
   * return both the data and a total count of the matched rows.
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
  public void testCoreExample2() {
    ReadResponse resp = factual.fetch("places", new Query()
    .field("name").beginsWith("Star")
    .includeRowCount());

    assertOk(resp);
    assertStartsWith(resp, "name", "Star");

    Map<String, Object> params = Maps.newHashMap();
    params.put("filters", JsonUtil.toJsonStr(
		new HashMap() {{  
			put("name", new HashMap() {{
					put("$bw", "Star");	    
			}});
	    }})
    );
	params.put("include_count", true);
    String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);
  }

  /**
   * Do a full-text search of the restaurant database for rows that match the
   * terms "Fried Chicken, Los Angeles"
   */
  @Test
  public void testCoreExample3() {
    ReadResponse resp = factual.fetch("places", new Query()
    .search("Fried Chicken, Los Angeles"));

    assertOk(resp);

    Map<String, Object> params = Maps.newHashMap();
    params.put("q", "Fried Chicken, Los Angeles");
    String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);

  }

  /**
   * To support paging in your app, return rows 20-25 of the full-text search result
   * from Example 3
   */
  @Test
  public void testCoreExample4() {
    ReadResponse resp = factual.fetch("places", new Query()
    .search("Fried Chicken, Los Angeles")
    .offset(20)
    .limit(5));

    assertOk(resp);
    assertEquals(5, resp.getData().size());

    Map<String, Object> params = Maps.newHashMap();
    params.put("q", "Fried Chicken, Los Angeles");
    params.put("offset", 20);
    params.put("limit", 5);
    String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);
    
  }

  /**
   * Return rows from the global places database with a name equal to "Stand"
   * within 5000 meters of the specified lat/lng
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
  public void testCoreExample5() {
    ReadResponse resp = factual.fetch("places", new Query()
    .field("name").equal("Stand")
    .within(new Circle(latitude, longitude, meters)));
    assertNotEmpty(resp);
    assertOk(resp);

    Map<String, Object> params = Maps.newHashMap();
    params.put("geo", JsonUtil.toJsonStr(
    	new HashMap() {{  
			put("$circle", new HashMap() {{
					put("$center", new String[]{Double.toString(latitude), Double.toString(longitude)});	    
					put("$meters", meters);
			}});
	    }})
    );
    params.put("filters", JsonUtil.toJsonStr(
		new HashMap() {{  
			put("name", new HashMap(){{
				put("$eq", "Stand");	    
			}});
	    }}
	 )
   );

    String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);

  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
  public void testSort_byDistance() {

    ReadResponse resp = factual.fetch("places", new Query()
    .within(new Circle(latitude, longitude, meters))
    .sortAsc("$distance"));

    assertNotEmpty(resp);
    assertOk(resp);
    assertAscendingDoubles(resp, "$distance");
    
    Map<String, Object> params = Maps.newHashMap();
    params.put("geo", JsonUtil.toJsonStr(
		new HashMap() {{  
			put("$circle", new HashMap(){{
				put("$center", new String[]{Double.toString(latitude), Double.toString(longitude)});	    
				put("$meters", meters);
			}});
		}})
    );
	params.put("sort", "$distance:asc");

	String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);
        
  }

  /**
   * {"$and":[{"name":{"$bw":"McDonald's"},"category":{"$bw":"Food & Beverage"}}]}
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
  public void testRowFilters_2beginsWith() {
    ReadResponse resp = factual.fetch("places", new Query()
    .field("name").beginsWith("McDonald's")
    .field("category").beginsWith("Food & Beverage"));

    assertOk(resp);
    assertStartsWith(resp, "name", "McDonald");
    assertStartsWith(resp, "category", "Food & Beverage");

    Map<String, Object> params = Maps.newHashMap();
    params.put("filters", JsonUtil.toJsonStr(
		new HashMap() {{  
			put("$and", new Map[] {
				new HashMap() {{
					put("name", new HashMap() {{
						put("$bw", "McDonald's");
					}});	
					put("category", new HashMap() {{
						put("$bw", "Food & Beverage");
					}});
				}}});
    	}})
    );

	String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);

  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
  public void testIn() {
    Query q = new Query().field("region").in("CA", "NM", "FL");
    ReadResponse resp = factual.fetch("places", q);

    assertOk(resp);
    assertNotEmpty(resp);
    assertIn(resp, "region", "CA", "NM", "FL");
    
    Map<String, Object> params = Maps.newHashMap();
    params.put("filters", JsonUtil.toJsonStr(
		new HashMap() {{  
			put("region", new HashMap(){{
				put("$in", new String[]{"CA", "NM", "FL"});	    
			}});
	    }})
    );

	String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);    
    
  }

  /**
   * Tests a top-level AND with a nested OR and an $in:
   * 
   * <pre>
   * {$and:[
   *   {region:{$in:["MA","VT","NH"]}},
   *   {$or:[
   *     {name:{$bw:"Star"}},
   *     {name:{$bw:"Coffee"}}]}]}
   * </pre>
   * @throws UnsupportedEncodingException
   */
  @Test
  public void testComplicated() {
    Query q = new Query();
    q.field("region").in("MA","VT","NH");
    q.or(
        q.field("name").beginsWith("Coffee"),
        q.field("name").beginsWith("Star")
    );

    ReadResponse resp = factual.fetch("places", q);

    assertOk(resp);
    assertNotEmpty(resp);
    assertIn(resp, "region", "MA", "VT", "NH");

    // assert name starts with (coffee || star)
    for(String name : resp.mapStrings("name")){
      assertTrue(
          name.toLowerCase().startsWith("coffee") ||
          name.toLowerCase().startsWith("star")
      );
    }
  }

  private void assertIn(ReadResponse resp, String field, String... elems) {
    for(String val : resp.mapStrings(field)){
      for(String elem : elems) {
        if(elem.equals(val)) {
          return;
        }
      }
      fail(val + " was not in " + Joiner.on(", ").join(elems));
    }
  }

  @Test
  public void testSimpleTel() {
    ReadResponse resp = factual.fetch("places", new Query()
    .field("tel").beginsWith("(212)"));

    assertStartsWith(resp, "tel", "(212)");

    assertOk(resp);
  }

  /**
   * Search for places with names that have the terms "Fried Chicken"
   */
  @Test
  public void testFullTextSearch_on_a_field() {
    ReadResponse resp = factual.fetch("places", new Query()
    .field("name").search("Fried Chicken"));

    for(String name : resp.mapStrings("name")) {
      assertTrue(name.toLowerCase().contains("frie") || name.toLowerCase().contains("fry") || name.toLowerCase().contains("chicken"));
    }
  }

  @Test
  public void testCrosswalk_ex1() {
    CrosswalkResponse resp =
      factual.fetch("places", new CrosswalkQuery()
      .factualId("97598010-433f-4946-8fd5-4a6dd1639d77"));
    List<Crosswalk> crosswalks = resp.getCrosswalks();

    assertOk(resp);
    assertFalse(crosswalks.isEmpty());
    assertFactualId(crosswalks, "97598010-433f-4946-8fd5-4a6dd1639d77");
  }

  @Test
  public void testCrosswalk_ex2() {
    CrosswalkResponse resp =
      factual.fetch("places", new CrosswalkQuery()
      .factualId("97598010-433f-4946-8fd5-4a6dd1639d77")
      .only("loopt"));
    List<Crosswalk> crosswalks = resp.getCrosswalks();

    assertOk(resp);
    assertEquals(1, crosswalks.size());
    assertFactualId(crosswalks, "97598010-433f-4946-8fd5-4a6dd1639d77");
    assertNamespace(crosswalks, "loopt");
  }

  @Test
  public void testCrosswalk_ex3() {
    CrosswalkResponse resp =
      factual.fetch("places", new CrosswalkQuery()
      .namespace("foursquare")
      .namespaceId("4ae4df6df964a520019f21e3"));
    List<Crosswalk> crosswalks = resp.getCrosswalks();

    assertOk(resp);
    assertFalse(crosswalks.isEmpty());
  }

  @Test
  public void testCrosswalk_ex4() {
    CrosswalkResponse resp =
      factual.fetch("places", new CrosswalkQuery()
      .namespace("foursquare")
      .namespaceId("4ae4df6df964a520019f21e3")
      .only("yelp"));
    List<Crosswalk> crosswalks = resp.getCrosswalks();

    assertOk(resp);
    assertFalse(crosswalks.isEmpty());
    assertNamespace(crosswalks, "yelp");
  }

  @Test
  public void testCrosswalk_limit() {
    CrosswalkResponse resp =
      factual.fetch("places", new CrosswalkQuery()
      .factualId("97598010-433f-4946-8fd5-4a6dd1639d77")
      .limit(1));
    List<Crosswalk> crosswalks = resp.getCrosswalks();

    assertOk(resp);
    assertEquals(1, crosswalks.size());
  }

  @Test
  public void testResolve_ex1() {
    ReadResponse resp =
      factual.fetch("places", new ResolveQuery()
      .add("name", "McDonalds")
      .add("address", "10451 Santa Monica Blvd")
      .add("region", "CA")
      .add("postcode", "90025"));

    assertOk(resp);
    assertNotEmpty(resp);
  }

  @Test
  public void testNear() {
    ReadResponse resp = factual.fetch("places", new Query()
    .search("cigars")
    .near("1801 avenue of the stars, century city, ca", 5000));

    assertOk(resp);
    assertNotEmpty(resp);
  }

  @Test
  public void testApiException() {
    Factual badness = new Factual("badkey", "badsecret");
    try{
      badness.fetch("places", new Query().field("region").equal("CA"));
      fail("Expected to catch a FactualApiException");
    } catch (FactualApiException e) {
      assertEquals(401, e.getResponse().getStatusCode());
      assertEquals("Unauthorized", e.getResponse().getStatusMessage());
      assertTrue(e.getRequestUrl().startsWith("http://api.v3.factual.com/t/places"));
    }
  }
  
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
  public void testSelect() {
  	Query select = new Query().field("country").equal("US").only("address", "country");
    assertEquals("[address, country]", Arrays.toString(select.getSelectFields()));

    ReadResponse resp = factual.fetch("places", select);
    assertOk(resp);
    assertAll(resp, "country", "US");
    
    Map<String, Object> params = Maps.newHashMap();
    params.put("filters", JsonUtil.toJsonStr(
		new HashMap() {{  
			put("country", new HashMap(){{
				put("$eq", "US");	    
			}});
	    }})
    );
	params.put("select", "address,country");
	String respRaw = factual.get("t/places", params);
    assertEquals(resp.getJson(), respRaw);      

  }
  
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
  public void testCustomRead1() {
	  
    Map<String, Object> params = Maps.newHashMap();
    params.put("filters", JsonUtil.toJsonStr(
		new HashMap() {{  
			put("region", new HashMap(){{
				put("$in", new String[]{"CA", "NM", "FL"});	    
			}});
	    }})
    );
	params.put("select", "address,country");
	String respString = factual.get("t/places", params);	  
	  
    assertTrue(respString != null && respString.length() > 0);
  }
  
  @Test
  public void testCustomRead2() {
    Map<String, Object> params = Maps.newHashMap();
    params.put("select", "name,category");
    params.put("include_count", true);
    
    String respString = factual.get("t/places", params);
    assertTrue(respString != null && respString.length() > 0);
  }

  /**
   * And should not be used for geo queries.  
   * However, neither should it throw an exception. 
   */
  @Test
  public void testInvalidAnd() {
    Query q = new Query();
    q.and(
    	q.field("category").beginsWith("Food"),
        q.within(new Circle(latitude, longitude, meters))
    );

    ReadResponse resp = factual.fetch("places", q);
    assertOk(resp);
  }
  
  @Test
  public void testFacet() {
	FacetQuery facet = new FacetQuery("region", "locality")
	.search("Starbucks")
	.maxValuesPerFacet(20)
	.minCountPerFacetValue(100)
	.includeRowCount();

	FacetResponse resp = factual.fetch("places", facet);
	//printFacetResponse(resp);
	//System.out.println(resp.getJson());
    assertOk(resp);
    assertTrue(resp.getData().size() > 0);
  }
  
  @Test
  public void testFacetFilter() {
	FacetQuery facet = new FacetQuery("locality")
    .field("region").in("MA","VT","NH");
    facet.and(facet.or(	facet.field("name").beginsWith("Coffee"),
    		   			facet.field("name").beginsWith("Star")),
    		  facet.field("locality").beginsWith("w"));
	FacetResponse resp = factual.fetch("places", facet);
    assertOk(resp);
    assertTrue(resp.getData().size() > 0);
  }
  
  @Test
  public void testFacetGeo() {
	FacetQuery facet = new FacetQuery("category").within(new Circle(latitude, longitude, meters));
	FacetResponse resp = factual.fetch("places", facet);
    assertOk(resp);
    assertTrue(resp.getData().size() > 0);
  }
  
  @Test
  public void testSuggestAdd() {
	Suggest write = new Suggest()
    .setValue("longitude", 100);
	SuggestResponse resp = factual.suggest("global", write, new Metadata().user("testuser"));
    assertOk(resp);
    assertTrue(resp.isNewEntity());
  }
  
  @Test
  public void testSuggestEdit() {
	Suggest write = new Suggest()
    .setValue("longitude", 100);
	SuggestResponse resp = factual.suggest("global", "0545b03f-9413-44ed-8882-3a9a461848da",write, new Metadata().user("testuser"));
    assertOk(resp);
    assertFalse(resp.isNewEntity());
  }

  @Test
  public void testSuggestDelete() {
	Suggest write = new Suggest()
    .removeValue("longitude");
	SuggestResponse resp = factual.suggest("global", "0545b03f-9413-44ed-8882-3a9a461848da",write, new Metadata().user("testuser"));
    assertOk(resp);
    assertFalse(resp.isNewEntity());
  }

  @Test
  public void testSuggestError() {
	Suggest write = new Suggest()
    .removeValue("longitude");
	FactualApiException exception = null;
	try {
		SuggestResponse resp = factual.suggest("global", "randomwrongid", write, new Metadata().user("testuser"));
	} catch (FactualApiException e) {
		exception = e;
	}
	assertTrue(exception != null);
  }
  
  @Test
  public void testFlagDuplicate() {
	FlagResponse resp = factual.flagDuplicate("global", "0545b03f-9413-44ed-8882-3a9a461848da", new Metadata().user("testuser"));
    assertOk(resp);
  }
  
  @Test
  public void testFlagInaccurate() {
	FlagResponse resp = factual.flagInaccurate("global", "0545b03f-9413-44ed-8882-3a9a461848da", new Metadata().user("testuser"));
    assertOk(resp);
  }

  @Test
  public void testFlagInappropriate() {
	FlagResponse resp = factual.flagInappropriate("global", "0545b03f-9413-44ed-8882-3a9a461848da", new Metadata().user("testuser"));
    assertOk(resp);
  }  

  @Test
  public void testFlagNonExistent() {
	FlagResponse resp = factual.flagNonExistent("global", "0545b03f-9413-44ed-8882-3a9a461848da", new Metadata().user("testuser"));
    assertOk(resp);
  }  
  
  @Test
  public void testFlagSpam() {
	FlagResponse resp = factual.flagSpam("global", "0545b03f-9413-44ed-8882-3a9a461848da", new Metadata().user("testuser"));
    assertOk(resp);
  }  

  @Test
  public void testFlagOther() {
	FlagResponse resp = factual.flagOther("global", "0545b03f-9413-44ed-8882-3a9a461848da", new Metadata().user("testuser"));
    assertOk(resp);
  }  
  
  @Test
  public void testDiff() {
	DiffsQuery diff = new DiffsQuery(1318890505254L);
	DiffsResponse resp = factual.fetch("places", diff);
    assertOk(resp);
  }
  
  @Test
  public void testMulti() {
	  Query q = new Query().limit(1);
	  factual.queueFetch("places", q.field("country").equal("US"));
	  factual.queueFetch("places", new Query().limit(1)); 
	  MultiResponse multi = factual.sendRequests();
	  for (Response resp : multi.getData()) {
		  System.out.println(resp);
	  }
  }
  
  /**
   * Test debug mode
   */
  @Test
  public void testDebug() {
	factual.debug(true);
    ReadResponse resp = factual.fetch("places",
        new Query().field("country").equal("US"));
    factual.debug(false);
    assertOk(resp);
    assertAll(resp, "country", "US");
  }
  
  private void printFacetResponse(FacetResponse resp) {
	Map<String, Map<String, Object>> data = resp.getData();
	for (String field : data.keySet()) {
		Map<String, Object> map = data.get(field);
		for (String facetValue : map.keySet()) {
			System.out.println(field+" : "+facetValue + " : "+map.get(facetValue));
		}
	}
	printResponse(resp);
  }
  
  private void printSuggestResponse(SuggestResponse resp) {
	  System.out.println("factual id: "+resp.getFactualId());
	  System.out.println("is new entity: "+resp.isNewEntity());
	  printResponse(resp);
  }
  
  private void printResponse(Response resp) {
	System.out.println("version: "+resp.getVersion());
	System.out.println("included row count: "+resp.getIncludedRowCount());
	System.out.println("total row count: "+resp.getTotalRowCount());
	System.out.println("status: "+resp.getStatus());
  }
  
  private void assertFactualId(List<Crosswalk> crosswalks, String id) {
    for(Crosswalk cw : crosswalks) {
      assertEquals(id, cw.getFactualId());
    }
  }

  private void assertNamespace(List<Crosswalk> crosswalks, String ns) {
    for(Crosswalk cw : crosswalks) {
      assertEquals(ns, cw.getNamespace());
    }
  }

  private static final void assertNotEmpty(Response resp) {
    assertFalse(resp.isEmpty());
  }

  private static final void assertOk(Response resp) {
    assertEquals("ok", resp.getStatus());
  }

  private void assertAll(ReadResponse resp, String field, String expected) {
    for(String out : resp.mapStrings(field)) {
      assertEquals(expected, out);
    }
  }

  private void assertStartsWith(ReadResponse resp, String field, String substr) {
    for(String out : resp.mapStrings(field)) {
      assertTrue(out.startsWith(substr));
    }
  }

  private void assertAscendingDoubles(ReadResponse resp, String field) {
    Double prev = Double.MIN_VALUE;
    for(Map<?, ?> rec : resp.getData()) {
      Double d = (Double)rec.get(field);
      assertTrue(d >= prev);
      prev = d;
    }
  }

  /**
   * Reads value from named file in src/test/resources
   */
  public static String read(String name) {
    try {
      File file = new File("src/test/resources/" + name);
      if(file.exists()) {
        return FileUtils.readFileToString(file).trim();
      } else {
        fail("You must provide " + file);
        System.err.println("You must provide " + file);
        throw new IllegalStateException("Could not find " + file);
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

}
