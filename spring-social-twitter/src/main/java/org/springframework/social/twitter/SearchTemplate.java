/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.social.twitter;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.social.twitter.support.extractors.SavedSearchResponseExtractor;
import org.springframework.social.twitter.support.extractors.TrendsListResponseExtractor;
import org.springframework.social.twitter.types.SavedSearch;
import org.springframework.social.twitter.types.SearchResults;
import org.springframework.social.twitter.types.Trend;
import org.springframework.social.twitter.types.Trends;
import org.springframework.social.twitter.types.Tweet;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.NumberUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Implementation of {@link SearchOperations}, providing a binding to Twitter's search and trend-oriented REST resources.
 * @author Craig Walls
 */
class SearchTemplate extends AbstractTwitterOperations implements SearchOperations {

	private final RestTemplate restTemplate;
	
	private SavedSearchResponseExtractor savedSearchExtractor;
	
	private TrendsListResponseExtractor trendsListExtractor;
	
	private TrendsListResponseExtractor weeklyTrendsListExtractor;
	
	public SearchTemplate(LowLevelTwitterApi lowLevelApi, RestTemplate restTemplate) {
		super(lowLevelApi);
		this.restTemplate = restTemplate;
		this.savedSearchExtractor = new SavedSearchResponseExtractor();
		this.trendsListExtractor = new TrendsListResponseExtractor(TrendsListResponseExtractor.LONG_TREND_DATE_FORMAT);
		this.weeklyTrendsListExtractor = new TrendsListResponseExtractor(TrendsListResponseExtractor.SIMPLE_TREND_DATE_FORMAT);
	}

	public SearchResults search(String query) {
		return search(query, 1, DEFAULT_RESULTS_PER_PAGE, 0, 0);
	}

	public SearchResults search(String query, int page, int resultsPerPage) {
		return search(query, page, resultsPerPage, 0, 0);
	}

	public SearchResults search(String query, int page, int resultsPerPage, int sinceId, int maxId) {
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("query", query);
		parameters.put("rpp", String.valueOf(resultsPerPage));
		parameters.put("page", String.valueOf(page));
		String searchUrl = SEARCH_URL;
		if (sinceId > 0) {
			searchUrl += "&since_id={since}";
			parameters.put("since", String.valueOf(sinceId));
		}
		if (maxId > 0) {
			searchUrl += "&max_id={max}";
			parameters.put("max", String.valueOf(maxId));
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> resultsMap = restTemplate.getForObject(searchUrl, Map.class, parameters);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) resultsMap.get("results");
		List<Tweet> tweets = new ArrayList<Tweet>(resultsMap.size());
		for (Map<String, Object> item : items) {
			tweets.add(populateTweetFromSearchResults(item));
		}
		return buildSearchResults(resultsMap, tweets);
	}

	public List<SavedSearch> getSavedSearches() {
		requireUserAuthorization();
		return getLowLevelTwitterApi().fetchObjects("saved_searches.json", savedSearchExtractor);
	}

	public SavedSearch getSavedSearch(long searchId) {
		requireUserAuthorization();
		return getLowLevelTwitterApi().fetchObject("saved_searches/show/" + searchId + ".json", savedSearchExtractor);
	}

	public void createSavedSearch(String query) {		
		requireUserAuthorization();
		MultiValueMap<String, Object> data = new LinkedMultiValueMap<String, Object>();
		data.set("query", query);
		getLowLevelTwitterApi().publish("saved_searches/create.json", data);
	}

	public void deleteSavedSearch(long searchId) {
		requireUserAuthorization();
		getLowLevelTwitterApi().delete("saved_searches/destroy/" + searchId + ".json");
	}
	
	// Trends

	public Trends getCurrentTrends() {
		return getCurrentTrends(false);
	}

	public Trends getCurrentTrends(boolean excludeHashtags) {
		String path = makeTrendPath("trends/current.json", excludeHashtags, null);
		return getLowLevelTwitterApi().fetchObject(path, trendsListExtractor).get(0);
	}

	public List<Trends> getDailyTrends() {
		return getDailyTrends(false, null);
	}

	public List<Trends> getDailyTrends(boolean excludeHashtags) {
		return getDailyTrends(excludeHashtags, null);
	}

	public List<Trends> getDailyTrends(boolean excludeHashtags, String startDate) {
		String path = makeTrendPath("trends/daily.json", excludeHashtags, startDate);
		return getLowLevelTwitterApi().fetchObject(path, trendsListExtractor);
	}
	
	public List<Trends> getWeeklyTrends() {
		return getWeeklyTrends(false, null);
	}
	
	public List<Trends> getWeeklyTrends(boolean excludeHashtags) {
		return getWeeklyTrends(excludeHashtags, null);
	}
	
	public List<Trends> getWeeklyTrends(boolean excludeHashtags, String startDate) {
		String path = makeTrendPath("trends/weekly.json", excludeHashtags, startDate);
		return getLowLevelTwitterApi().fetchObject(path, weeklyTrendsListExtractor);
	}

	public Trends getLocalTrends(long whereOnEarthId) {
		return getLocalTrends(whereOnEarthId, false);
	}

	public Trends getLocalTrends(long whereOnEarthId, boolean excludeHashtags) {
		Map<String, String> params = new HashMap<String, String>();
		if(excludeHashtags) {
			params.put("exclude", "hashtags");
		}
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> response = getLowLevelTwitterApi().fetchObject("trends/" + whereOnEarthId + ".json", List.class, params);
		@SuppressWarnings("unchecked")
		List<Map<String, String>> trendMapList = (List<Map<String, String>>) response.get(0).get("trends");
		List<Trend> trendList = new ArrayList<Trend>(trendMapList.size());
		for (Map<String, String> trendMap : trendMapList) {
			trendList.add(new Trend(trendMap.get("name"), trendMap.get("query")));
		}
		String dateString = String.valueOf(response.get(0).get("created_at"));
		return new Trends(toDate(dateString, localTrendDateFormat), trendList);
	}

	private String makeTrendPath(String basePath, boolean excludeHashtags, String startDate) {
		String url = basePath + (excludeHashtags || startDate != null ? "?" : "");
		url += excludeHashtags ? "exclude=hashtags" : "";
		url += excludeHashtags && startDate != null ? "&" : "";
		url += startDate != null ? "date=" + startDate : "";
		return url;
	}

	private SearchResults buildSearchResults(Map<String, Object> response, List<Tweet> tweets) {
		Number maxId = response.containsKey("max_id") ? (Number) response.get("max_id") : 0;
		Number sinceId = response.containsKey("since_id") ? (Number) response.get("since_id") : 0;
		return new SearchResults(tweets, maxId.longValue(), sinceId.longValue(), response.get("next_page") == null);
	}

	private Tweet populateTweetFromSearchResults(Map<String, Object> item) {
		Tweet tweet = new Tweet();
		tweet.setId(NumberUtils.parseNumber(ObjectUtils.nullSafeToString(item.get("id")), Long.class));
		tweet.setFromUser(ObjectUtils.nullSafeToString(item.get("from_user")));
		tweet.setText(ObjectUtils.nullSafeToString(item.get("text")));
		tweet.setCreatedAt(toDate(ObjectUtils.nullSafeToString(item.get("created_at")), searchDateFormat));
		tweet.setFromUserId(NumberUtils.parseNumber(ObjectUtils.nullSafeToString(item.get("from_user_id")), Long.class));
		Object toUserId = item.get("to_user_id");
		if (toUserId != null) {
			tweet.setToUserId(NumberUtils.parseNumber(ObjectUtils.nullSafeToString(toUserId), Long.class));
		}
		tweet.setLanguageCode(ObjectUtils.nullSafeToString(item.get("iso_language_code")));
		tweet.setProfileImageUrl(ObjectUtils.nullSafeToString(item.get("profile_image_url")));
		tweet.setSource(ObjectUtils.nullSafeToString(item.get("source")));
		return tweet;
	}

	private static final DateFormat searchDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
	private static final DateFormat localTrendDateFormat = new SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss'Z'");

	// 2011-03-18T16:45:33Z

	private static Date toDate(String dateString, DateFormat dateFormat) {
		try {
			return dateFormat.parse(dateString);
		} catch (ParseException e) {
			return null;
		}
	}

	static final int DEFAULT_RESULTS_PER_PAGE = 50;

	private static final String SEARCH_API_URL_BASE = "https://search.twitter.com";
	private static final String SEARCH_URL = SEARCH_API_URL_BASE + "/search.json?q={query}&rpp={rpp}&page={page}";
}
