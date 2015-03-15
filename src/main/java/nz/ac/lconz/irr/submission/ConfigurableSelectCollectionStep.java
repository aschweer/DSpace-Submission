package nz.ac.lconz.irr.submission;

import org.dspace.app.xmlui.aspect.submission.AbstractSubmissionStep;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.handle.HandleManager;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * DSpace submission step that allows the submitter to select the destination collection.
 * This is very similar to the default DSpace "Select Collection" step. The only difference
 * is that the default step lists all collections, while this step lists only collections
 * that are children of the communities listed (with their handle) in a configuration field.
 *
 * The configuration field used is <code>submission.parent-communities</code>
 * in the lconz-extras configuration module. The value of this field should be
 * a comma-separated list of community handles.
 *
 *
 * @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ
 */
public class ConfigurableSelectCollectionStep extends AbstractSubmissionStep {


	/** Language Strings */
	protected static final Message T_head =
			message("xmlui.Submission.submit.SelectCollection.head");
	protected static final Message T_collection =
			message("xmlui.Submission.submit.SelectCollection.collection");
	protected static final Message T_collection_help =
			message("xmlui.Submission.submit.SelectCollection.collection_help");
	protected static final Message T_collection_default =
			message("xmlui.Submission.submit.SelectCollection.collection_default");
	protected static final Message T_submit_next =
			message("xmlui.general.next");

	public ConfigurableSelectCollectionStep()
	{
		this.requireHandle = true;
	}

	public void addPageMeta(PageMeta pageMeta) throws SAXException,
			                                                  WingException
	{

		pageMeta.addMetadata("title").addContent(T_submission_title);
		pageMeta.addTrailLink(contextPath + "/",T_dspace_home);
		pageMeta.addTrail().addContent(T_submission_trail);
	}

	public void addBody(Body body) throws SAXException, WingException,
			                                      UIException, SQLException, IOException, AuthorizeException
	{
		java.util.List<Collection> collections = getSelectableCollections();

		String actionURL = contextPath + "/submit/" + knot.getId() + ".continue";
		// Basic form with a drop down list of all the collections
		// you can submit too.
		Division div = body.addInteractiveDivision("select-collection",actionURL,Division.METHOD_POST,"primary submission");
		div.setHead(T_submission_head);

		List list = div.addList("select-collection", List.TYPE_FORM);
		list.setHead(T_head);
		Select select = list.addItem().addSelect("handle");
		select.setLabel(T_collection);
		select.setHelp(T_collection_help);

		select.addOption("",T_collection_default);
		for (Collection collection : collections)
		{
			String name = collection.getMetadata("name");
			if (name.length() > 50)
			{
				name = name.substring(0, 47) + "...";
			}
			select.addOption(collection.getHandle(),name);
		}

		Button submit = list.addItem().addButton("submit");
		submit.setValue(T_submit_next);
	}


	/**
	 * Each submission step must define its own information to be reviewed
	 * during the final Review/Verify Step in the submission process.
	 * <P>
	 * The information to review should be tacked onto the passed in
	 * List object.
	 * <P>
	 * NOTE: To remain consistent across all Steps, you should first
	 * add a sub-List object (with this step's name as the heading),
	 * by using a call to reviewList.addList().   This sublist is
	 * the list you return from this method!
	 *
	 * @param reviewList
	 *      The List to which all reviewable information should be added
	 * @return
	 *      The new sub-List object created by this step, which contains
	 *      all the reviewable information.  If this step has nothing to
	 *      review, then return null!
	 */
	public List addReviewSection(List reviewList) throws SAXException,
			                                                     WingException, UIException, SQLException, IOException,
			                                                     AuthorizeException
	{
		//Currently, the selecting a Collection is not reviewable in DSpace,
		//since it cannot be changed easily after creating the item
		return null;
	}

	/**
	 * Recycle
	 */
	public void recycle()
	{
		this.handle = null;
		super.recycle();
	}

	private java.util.List<Collection> getSelectableCollections() throws SQLException {
		java.util.List<Collection> collections; // List of possible collections.

		String communitiesProp = ConfigurationManager.getProperty("lconz-extras", "submission.parent-communities");
		if (communitiesProp == null || "".equals(communitiesProp)) {
			collections = Arrays.asList(Collection.findAll(context));
		} else {
			collections = new ArrayList<Collection>();
			for (String communityHandle : communitiesProp.split(",\\s*")) {
				DSpaceObject submissionCommunity = HandleManager.resolveToObject(context, communityHandle);
				if (submissionCommunity != null) {
					addSelectableCollections(collections, (Community) submissionCommunity);
				}
			}
		}

		Collections.sort(collections, new Comparator<Collection>() {
			public int compare(Collection o1, Collection o2) {
				if (o1.getName() != null && o2.getName() != null) {
					return (o1.getName().compareToIgnoreCase(o2.getName()));
				}
				return new Integer(o1.getID()).compareTo(o2.getID());
			}
		});
		return collections;
	}

	private void addSelectableCollections(java.util.List<Collection> collections, Community community) throws SQLException {
		if (community == null) {
			return;
		}
		// now get all collections within this community to which the user can add items
		// if for some reason the handle is wrong, submissionCommunity will be null
		// and the following statement will simply get _all_ collections that the user can add items to
		collections.addAll(Arrays.asList(Collection.findAuthorized(context, community, Constants.ADD))); // List of possible collections.
		// List of possible collections.
		Community[] subCommunities = community.getSubcommunities();
		for (Community subCommunity : subCommunities)
		{
			// add subcommunity's collections too
			addSelectableCollections(collections, subCommunity);
		}
	}
}
