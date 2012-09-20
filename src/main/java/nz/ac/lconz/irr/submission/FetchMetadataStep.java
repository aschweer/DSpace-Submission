package nz.ac.lconz.irr.submission;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.codehaus.jackson.map.ObjectMapper;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.submit.AbstractProcessingStep;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FetchMetadataStep extends org.dspace.submit.AbstractProcessingStep {

	@Override
	public int doProcessing(Context arg0, HttpServletRequest request,
			HttpServletResponse response, SubmissionInfo submission)
			throws ServletException, IOException, SQLException,
			AuthorizeException {
        try {
            String doiString = getDoi(request);
            boolean clearExisting = overrideExistingValues(request);
            // get jackson tree from web
            Map<String, Object> crossrefMetadata = getCrossrefMetadata(doiString);
            // get mapping jackson -> md fields
            Map<String, String> metadataMapping = getMetadataMapping(ConfigurationManager.getProperty("lconz-extras", "submission.crossref.mapping"));
            // actually fill in item
            fillInMetadata(submission.getSubmissionItem().getItem(), metadataMapping, crossrefMetadata, clearExisting);

	        boolean doCitation = ConfigurationManager.getBooleanProperty("lconz-extras", "submission.crossref.citation", false);
	        if (doCitation) {
		        String citationStyle = ConfigurationManager.getProperty("lconz-extras", "submission.crossref.citation.style");
		        if (citationStyle == null || "".equals(citationStyle)) {
			        citationStyle = "apa"; // use APA as default citation style
		        }
		        String citationLocale = ConfigurationManager.getProperty("lconz-extras", "submission.crossref.citation.locale");
		        if (citationLocale == null || "".equals(citationLocale)) {
			        citationLocale = "en-GB"; // use British English as default citatation language
		        }
		        String citation = getCrossrefCitation(doiString, citationStyle, citationLocale);
		        String field = ConfigurationManager.getProperty("lconz-extras", "submission.crossref.citatation.field");
		        if (field == null || "".equals(field)) {
			        field = "dc.identifier.citation";
		        }
		        fillInCitation(submission.getSubmissionItem().getItem(), field, citation, citationLocale.replaceAll("-", "_"), clearExisting);
	        }
        } catch (IOException ioe) {
            super.addErrorMessage(1, "An error occurred while retrieving metadata for this item: " + ioe.getMessage());
        }
        return AbstractProcessingStep.STATUS_COMPLETE;
    }

	private void fillInCitation(Item item, String field, String citation, String locale, boolean clearExisting) throws AuthorizeException, SQLException {
		String[] components = field.split("\\.");
		String schema = components[0];
		String element = components[1];
		String qualifier = components.length > 2 ? components[2] : null;

		boolean changes = false;

		if (clearExisting) {
			item.clearMetadata(schema, element, qualifier, Item.ANY);
			changes = true;
		}

		if (citation != null && !"".equals(citation)) {
			item.addMetadata(schema, element, qualifier, locale, citation);
			changes = true;
		}

		if (changes) {
			item.update();
		}
	}

	private void fillInMetadata(Item item, Map<String, String> metadataMapping, Map<String, Object> citationData, boolean clearExisting) throws AuthorizeException, SQLException {
        if (metadataMapping == null || metadataMapping.isEmpty()) {
            return;
        }
        boolean changes = false;
        for (String key : citationData.keySet()) {
            if (metadataMapping.containsKey(key)) {
                String fieldName = metadataMapping.get(key);
                String style = null;
                Pattern stylePattern = Pattern.compile("(.*)\\((\\w+)\\)$");
                Matcher matcher = stylePattern.matcher(fieldName);
                if (matcher.matches()) {
                    fieldName = matcher.group(1);
                    style = matcher.group(2);
                }

                List<String> values = extractFieldValues(style, citationData.get(key));

                String[] field = fieldName.split("\\.");
                String schema = field[0];
                String element = field[1];
                String qualifier = field.length > 2 ? field[2] : null;

                if (clearExisting) {
                    item.clearMetadata(schema, element, qualifier, Item.ANY);
                }
                for (String value : values) {
                    item.addMetadata(schema, element, qualifier, null, value);
                }
                changes = true;
            }
        }
        if (changes) {
            item.update();
        }
    }

    List<String> extractFieldValues(String style, Object data) {
        List<String> result = new ArrayList<String>();
        if (data instanceof String) {
            result.add((String) data);
        } else if (data instanceof List<?>) {
            List<Object> dataList = (List<Object>) data;
            List<String> listResult = new ArrayList<String>();
            for (Object dataItem : dataList) {
                listResult.addAll(extractFieldValues(style, dataItem));
            }
            result.addAll(listResult);
        } else if (data instanceof Map<?, ?>) {
            Map<String, Object> dataMap = (Map<String, Object>) data;
            if (style.equals("date") && dataMap.containsKey("date-parts"))  {
                List<Object> datesList = (List<Object>) dataMap.get("date-parts");
                List<Integer> parts = (List<Integer>) datesList.get(0);

                StringBuilder date = new StringBuilder();
                date.append(parts.get(0));
                if (parts.size() > 1) {
                    date.append("-");
                    Integer month = parts.get(1);
                    if (month < 10) {
                        date.append("0");
                    }
                    date.append(month);
                }
                if (parts.size() > 2) {
                    date.append("-");
                    Integer day = parts.get(2);
                    if (day < 10) {
                        date.append("0");
                    }
                    date.append(day);
                }

                result.add(date.toString());
            } else if (style.equals("name")) {
                StringBuilder name = new StringBuilder();
                
                // last name
                if (dataMap.containsKey("family")) {
                    name.append(dataMap.get("family"));
                } else {
                    name.append("?");
                }
                
                name.append(", ");
                
                // first name
                if (dataMap.containsKey("given")) {
                    name.append(dataMap.get("given"));
                } else {
                    name.append("?");
                }

                result.add(name.toString());
            }
        }
        return result;
    }

    Map<String, String> getMetadataMapping(String fileName) {
        if (fileName == null || fileName.length() == 0) {
            super.addErrorMessage(1, "No mappings file specified (via lconz-extras config file, key submission.crossref.mapping)");
            return null;
        }
        File file = new File(fileName);
        Map<String, String> metadataMapping = new HashMap<String, String>();
        Scanner scanner;
        try {
            scanner = new Scanner(file);
        } catch (FileNotFoundException e) {
            super.addErrorMessage(1, "Invalid mappings file " + fileName + " (specified via lconz-extras config file, key submission.crossref.mapping)");
            return null;
        }
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.startsWith("#")) {
                continue;
            }
            String[] components = line.split("\\s*=\\s*");
            if (components.length == 2) {
                metadataMapping.put(components[0], components[1]);
            } else {
                super.addErrorMessage(2, "Invalid mapping specified, ignoring: " + line);
                continue;
            }
        }

        return metadataMapping;
    }

    Map<String, Object> getCrossrefMetadata(String doiString) throws IOException {
        Map result;
        GetMethod get = new GetMethod("http://dx.doi.org/" + doiString);
        try {
            HttpClient client = new HttpClient();
            client.getHttpConnectionManager().getParams().setConnectionTimeout(1000);
            get.addRequestHeader("Accept", "application/citeproc+json");

            client.executeMethod(get);

            ObjectMapper mapper = new ObjectMapper();
            result = mapper.readValue(get.getResponseBodyAsStream(), Map.class);
        } finally {
            get.releaseConnection();
        }
        return result;
    }

	String getCrossrefCitation(String doiString, String citationStyle, String citationLocale) throws IOException {
		String result;
		GetMethod get = new GetMethod("http://dx.doi.org/" + doiString);
		try {
			HttpClient client = new HttpClient();
			client.getHttpConnectionManager().getParams().setConnectionTimeout(1000);
			get.addRequestHeader("Accept", "text/x-bibliography; style=" + citationStyle + "; locale=" + citationLocale);

			client.executeMethod(get);

			result = get.getResponseBodyAsString(2000);
		} finally {
			get.releaseConnection();
		}
		return result;
	}

	String getDoi(HttpServletRequest request) {
        String parameter = request.getParameter("doi");
        return parameter;
	}

    private boolean overrideExistingValues(HttpServletRequest request) {
        String overrideParam = request.getParameter("override");
        if (overrideParam == null) {
            return false;
        }
        return overrideParam.equals("override");
    }

	@Override
	public int getNumberOfPages(HttpServletRequest request, SubmissionInfo submission)
			throws ServletException {
		return 1;
	}

}
