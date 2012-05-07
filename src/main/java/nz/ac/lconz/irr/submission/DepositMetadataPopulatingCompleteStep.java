package nz.ac.lconz.irr.submission;

import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.submit.step.CompleteStep;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ
 */
public class DepositMetadataPopulatingCompleteStep extends CompleteStep {
	private static Logger log = Logger.getLogger(DepositMetadataPopulatingCompleteStep.class);

	public static final String MODULE = "lconz-extras";

	public static final String DATE_FIELD_KEY = "submission.date.field";

	public static final String METHOD_FIELD_KEY = "submission.method.field";
	public static final String METHOD_VALUE = "dspace deposit";


	@Override
	public int doProcessing(Context context, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, SubmissionInfo submissionInfo) throws ServletException, IOException, SQLException, AuthorizeException {
		Item item = submissionInfo.getSubmissionItem().getItem();
		populateDepositDate(item);
		populateDepositMethod(item);
		return super.doProcessing(context, httpServletRequest, httpServletResponse, submissionInfo);
	}

	private void populateDepositMethod(Item item) {
		String schema = null;
		String element = null;
		String qualifier = null;
		String fieldName = ConfigurationManager.getProperty(MODULE, METHOD_FIELD_KEY);
		if (fieldName != null) {
			try {
			String[] components = fieldName.split("\\.");
			schema = components[0];
			element = components[1];
			if (components.length > 2) {
				qualifier = components[2];
			}
			} catch (ArrayIndexOutOfBoundsException e) { /*ignore*/ }
		}
		if (schema == null || element == null) {
			log.error("Cannot populate deposit method metadata field; no field configured (" + METHOD_FIELD_KEY + " in module " + MODULE + ")");
			return;
		}

		item.clearMetadata(schema, element, qualifier, Item.ANY);
		item.addMetadata(schema, element, qualifier, null, METHOD_VALUE);

		log.info("Set deposit method metadata on item id=" + item.getID() + " to " + METHOD_VALUE);

		try {
			item.update();
		} catch (SQLException e) {
			log.error("Problem populating deposit metadata: " + e.getMessage(), e);
		} catch (AuthorizeException e) {
			log.error("Problem populating deposit metadata: " + e.getMessage(), e);
		}
	}

	private void populateDepositDate(Item item) {
		String schema = null;
		String element = null;
		String qualifier = null;
		String fieldName = ConfigurationManager.getProperty(MODULE, DATE_FIELD_KEY);
		if (fieldName != null) {
			try {
				String[] components = fieldName.split("\\.");
				schema = components[0];
				element = components[1];
				if (components.length > 2) {
					qualifier = components[2];
				}
			} catch (ArrayIndexOutOfBoundsException e) { /*ignore*/ }
		}
		if (schema == null || element == null) {
			log.error("Cannot populate deposit date metadata field; no field configured (" + DATE_FIELD_KEY + " in module " + MODULE + ")");
			return;
		}

		item.clearMetadata(schema, element, qualifier, Item.ANY);
		DCDate now = new DCDate(new Date());
		item.addMetadata(schema, element, qualifier, null, now.toString());

		log.info("Set deposit date metadata on item id=" + item.getID() + " to " + now.toString());
	}

	@Override
	public int getNumberOfPages(HttpServletRequest httpServletRequest, SubmissionInfo submissionInfo) throws ServletException {
		return super.getNumberOfPages(httpServletRequest, submissionInfo);
	}
}
