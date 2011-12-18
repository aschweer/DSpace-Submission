package nz.ac.lconz.irr.submission;

import org.dspace.app.xmlui.aspect.submission.AbstractSubmissionStep;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;

public class InputDOIStep extends AbstractSubmissionStep {

    private final String T_head = "DOI";
    private final String T_help = "If you have a DOI for the item, you can enter it here and DSpace will attempt to fill in some item metadata automatically by querying CrossRef.";
    private final String T_doi_label = "DOI";
    private final String T_doi_help = "Please leave out the resolver part of the DOI - for example, use 10.1038/nrd842 rather than http://dx.doi.org/10.1038/nrd842.";
    private final String T_override_label = "Existing Metadata";
    private final String T_override_help = "If this box is ticked, then metadata values from CrossRef will replace any values for the same field that already exist in the item.";
    private final String T_override_text = "Clear";

    @Override
    public void addBody(Body body) throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException {
        Collection collection = submission.getCollection();
        String actionURL = contextPath + "/handle/"+collection.getHandle() + "/submit/" + knot.getId() + ".continue";

        Division div = body.addInteractiveDivision("submit-doi", actionURL, Division.METHOD_POST, "primary submission");
        div.setHead(T_submission_head);
        addSubmissionProgressList(div);

        List form = div.addList("submit-doi", List.TYPE_FORM);
        form.setHead(T_head);

        form.addItem(T_help);

        Text doiField = form.addItem().addText("doi");
        doiField.setLabel(T_doi_label);
        doiField.setHelp(T_doi_help);

        CheckBox overrideBox = form.addItem().addCheckBox("override");
        overrideBox.addOption("override", T_override_text);
        overrideBox.setOptionSelected("override");
        overrideBox.setLabel(T_override_label);
        overrideBox.setHelp(T_override_help);

        addControlButtons(form);
    }

    @Override
    public List addReviewSection(List list) throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException {
        return null;
    }
}
