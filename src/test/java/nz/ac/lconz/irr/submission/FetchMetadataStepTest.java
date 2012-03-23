package nz.ac.lconz.irr.submission;

import org.codehaus.jackson.map.ObjectMapper;
import org.dspace.content.Item;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: andrea
 * Date: 18/12/11
 * Time: 8:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class FetchMetadataStepTest {
    @org.junit.Test
    public void testReadJSon() {
        FetchMetadataStep step = new FetchMetadataStep();
        Map<String, Object> data;
        try {
            data = step.getCitationData("10.1038/nrd842");
            assert data != null;
            assert !data.isEmpty();
            assert data.containsKey("DOI");
            assert data.get("DOI").equals("10.1038/nrd842");
        } catch (IOException e) {
            e.printStackTrace();
            assert false;
        }
    }
    
    @Test
    public void testReadMappingsFile() {
        FetchMetadataStep step = new FetchMetadataStep();
        String filename = "config/modules/crossref-doi-map.txt";
	    URL resource = ClassLoader.getSystemResource(filename);
        Map<String, String> mapping = step.getMetadataMapping(resource.getFile());
        assert mapping != null;
        assert !mapping.isEmpty();
        assert mapping.containsKey("author");
        assert mapping.get("author").equals("dc.contributor.author(name)");
    }

    @Test
    public void testExtractFieldValueSimple() {
        FetchMetadataStep step = new FetchMetadataStep();
        Map<String, Object> data;
        try {
            ObjectMapper mapper = new ObjectMapper();
            data = mapper.readValue(getCitationFile(), Map.class);
            List values = step.extractFieldValues(null, data.get("title"));
            assert(values.size() == 1);
            assert values.get(0).equals("From the analyst's couch: Selective anticancer drugs");
        } catch (IOException e) {
            e.printStackTrace();
            assert false;
        }
    }

	private File getCitationFile() {
		return new File(ClassLoader.getSystemResource("citation.json").getFile());
	}

	@Test
    public void testExtractFieldValueList() {
        FetchMetadataStep step = new FetchMetadataStep();
        Map<String, Object> data;
        try {
            ObjectMapper mapper = new ObjectMapper();
            data = mapper.readValue(getCitationFile(), Map.class);
            List values = step.extractFieldValues("name", data.get("author"));
            assert(values.size() == 2);
            assert values.contains("Atkins, Joshua H.");
            assert values.contains("Gershell, Leland J.");
        } catch (IOException e) {
            e.printStackTrace();
            assert false;
        }
    }

    @Test
    public void testExtractDate() {
        FetchMetadataStep step = new FetchMetadataStep();
        Map<String, Object> data;
        try {
            ObjectMapper mapper = new ObjectMapper();
            data = mapper.readValue(getCitationFile(), Map.class);
            List values = step.extractFieldValues("date", data.get("issued"));
            assert(values.size() == 1);
            assert values.contains("2002-07");
        } catch (IOException e) {
            e.printStackTrace();
            assert false;
        }
    }
}
