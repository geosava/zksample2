package de.forsthaus.webui.branch;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

import org.springframework.dao.DataAccessException;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zkplus.databind.BindingListModelList;
import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Tab;
import org.zkoss.zul.Tabbox;
import org.zkoss.zul.Tabpanel;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;

import com.trg.search.Filter;

import de.forsthaus.UserWorkspace;
import de.forsthaus.backend.model.Branche;
import de.forsthaus.backend.service.BrancheService;
import de.forsthaus.backend.util.HibernateSearchObject;
import de.forsthaus.webui.util.ButtonStatusCtrl;
import de.forsthaus.webui.util.FDUtils;
import de.forsthaus.webui.util.GFCBaseCtrl;
import de.forsthaus.webui.util.MultiLineMessageBox;

/**
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++<br>
 * Main controller for the branch main module.<br>
 * <br>
 * zul-file: /WEB-INF/pages/branch/branchMain.zul.<br>
 * <br>
 * This class creates the Tabs + TabPanels. The components/data to all tabs are
 * created on demand on first time selecting the tab.<br>
 * This controller holds all getters/setters for the used databinding beans/sets
 * in all related tabs. In the child tabs controllers their databinding
 * setters/getters pointed to this mainTabController.<br>
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++<br>
 * <br>
 * 
 * @changes 07/03/2010: sge modified for zk5.x with complete Annotated
 *          Databinding.<br>
 * 
 *          Managed tabs:<br>
 *          - BranchListCtrl = Branch List / BranchenListe<br>
 *          - BranchDetailCtrl = Branch Details / BranchenDetails<br>
 * 
 * @author bbruhns
 * @author sgerth
 */
public class BranchMainCtrl extends GFCBaseCtrl implements Serializable {

	private static final long serialVersionUID = 1L;

	/*
	 * ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	 * All the components that are defined here and have a corresponding
	 * component with the same 'id' in the zul-file are getting autowired by our
	 * 'extends GFCBaseCtrl' GenericForwardComposer.
	 * ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	 */
	protected Window windowBranchMain; // autowired

	// Tabs
	protected Tabbox tabbox_BranchMain; // autowired
	protected Tab tabBranchList; // autowired
	protected Tab tabBranchDetail; // autowired
	protected Tabpanel tabPanelBranchList; // autowired
	protected Tabpanel tabPanelBranchDetail; // autowired

	// filter components
	protected Checkbox checkbox_Branch_ShowAll; // autowired
	protected Textbox tb_Branch_Name; // aurowired

	// checkRights
	protected Button button_BranchMain_PrintBranches;
	protected Button button_BranchMain_Search_BranchName;

	// Button controller for the CRUD buttons
	private final String btnCtroller_ClassPrefix = "button_BranchMain_";
	private ButtonStatusCtrl btnCtrlBranch;
	protected Button btnNew; // autowired
	protected Button btnEdit; // autowired
	protected Button btnDelete; // autowired
	protected Button btnSave; // autowired
	protected Button btnCancel; // autowired
	protected Button btnClose; // autowired

	protected Button btnHelp;

	// Tab-Controllers for getting access to their components/dataBinders
	private BranchListCtrl branchListCtrl;
	private BranchDetailCtrl branchDetailCtrl;

	// Databinding
	private Branche branche;
	private Branche selectedBranche;
	private BindingListModelList branches;

	// ServiceDAOs / Domain Classes
	private BrancheService brancheService;

	// always a copy from the bean before modifying. Used for reseting
	private Branche originalBranche;

	/**
	 * default constructor.<br>
	 */
	public BranchMainCtrl() {
		super();

		FDUtils.logEventDebug(this, "super()");
	}

	@Override
	public void doAfterCompose(Component window) throws Exception {
		super.doAfterCompose(window);

		/**
		 * 1. Set an 'alias' for this composer name to access it in the
		 * zul-file.<br>
		 * 2. Set the parameter 'recurse' to 'false' to avoid problems with
		 * managing more than one zul-file in one page. Otherwise it would be
		 * overridden and can ends in curious error messages.
		 */
		self.setAttribute("controller", this, false);
	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++ //
	// +++++++++++++++ Component Events ++++++++++++++++ //
	// +++++++++++++++++++++++++++++++++++++++++++++++++ //

	/**
	 * Automatically called method from zk.
	 * 
	 * @param event
	 * @throws Exception
	 */
	public void onCreate$windowBranchMain(Event event) throws Exception {
		FDUtils.logEventDebug(this, event);

		// create the Button Controller. Disable not used buttons during working
		btnCtrlBranch = new ButtonStatusCtrl(getUserWorkspace(), btnCtroller_ClassPrefix, false, btnNew, btnEdit, btnDelete, btnSave, btnCancel, btnClose);

		doCheckRights();

		/**
		 * Initiate the first loading by selecting the customerList tab and
		 * create the components from the zul-file.
		 */
		tabBranchList.setSelected(true);
		if (tabPanelBranchList != null) {
			FDUtils.createTabPanelContent(tabPanelBranchList, this, "ModuleMainController", "/WEB-INF/pages/branch/branchList.zul");
		}

		// Set the buttons for editMode
		btnCtrlBranch.setInitEdit();
	}

	/**
	 * When the tab 'tabBranchList' is selected.<br>
	 * Loads the zul-file into the tab.
	 * 
	 * @param event
	 * @throws IOException
	 */
	public void onSelect$tabBranchList(Event event) throws IOException {
		FDUtils.logEventDebug(this, event);

		// Check if the tabpanel is already loaded
		if (tabPanelBranchList.getFirstChild() != null) {
			tabBranchList.setSelected(true);
			return;
		}

		if (tabPanelBranchList != null) {
			FDUtils.createTabPanelContent(tabPanelBranchList, this, "ModuleMainController", "/WEB-INF/pages/branch/branchList.zul");
		}
	}

	/**
	 * When the tab 'tabPanelBranchDetail' is selected.<br>
	 * Loads the zul-file into the tab.
	 * 
	 * @param event
	 * @throws IOException
	 */
	public void onSelect$tabBranchDetail(Event event) throws IOException {
		FDUtils.logEventDebug(this, event);

		// Check if the tabpanel is already loaded
		if (tabPanelBranchDetail.getFirstChild() != null) {
			tabBranchDetail.setSelected(true);

			// refresh the Binding mechanism
			getBranchDetailCtrl().setBranche(getSelectedBranche());
			getBranchDetailCtrl().getBinder().loadAll();
			return;
		}

		if (tabPanelBranchDetail != null) {
			FDUtils.createTabPanelContent(tabPanelBranchDetail, this, "ModuleMainController", "/WEB-INF/pages/branch/branchDetail.zul");
		}
	}

	/**
	 * when the checkBox 'Show All' for filtering is checked. <br>
	 * 
	 * @param event
	 */
	public void onCheck$checkbox_Branch_ShowAll(Event event) {
		doBranchListShowAll(event);
	}

	/**
	 * when the "print branches list" button is clicked.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	public void onClick$button_BranchMain_PrintBranches(Event event) throws InterruptedException {
		FDUtils.logEventDebug(this, event);

		FDUtils.doShowNotImplementedMessage();
	}

	/**
	 * When the "search for branch name" button is clicked.
	 * 
	 * @param event
	 * @throws Exception
	 */
	public void onClick$button_BranchMain_Search_BranchName(Event event) throws Exception {
		doBranchListSearchLikeBranchName(event);
	}

	/**
	 * When the "help" button is clicked.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	public void onClick$btnHelp(Event event) throws InterruptedException {
		doHelp(event);
	}

	/**
	 * When the "new" button is clicked.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	public void onClick$btnNew(Event event) throws InterruptedException {
		doNew(event);
	}

	/**
	 * When the "save" button is clicked.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	public void onClick$btnSave(Event event) throws InterruptedException {
		doSave(event);
	}

	/**
	 * When the "cancel" button is clicked.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	public void onClick$btnEdit(Event event) throws InterruptedException {
		doEdit(event);
	}

	/**
	 * When the "delete" button is clicked.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	public void onClick$btnDelete(Event event) throws InterruptedException {
		doDelete(event);
	}

	/**
	 * When the "cancel" button is clicked.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	public void onClick$btnCancel(Event event) throws InterruptedException {
		doCancel(event);
	}

	/**
	 * when the "refresh" button is clicked. <br>
	 * <br>
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	public void onClick$btnRefresh(Event event) throws InterruptedException {
		doResizeSelectedTab(event);
	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++ //
	// +++++++++++++++++ Business Logic ++++++++++++++++ //
	// +++++++++++++++++++++++++++++++++++++++++++++++++ //

	/**
	 * Filter the branch list with 'like branch name'. <br>
	 */
	private void doBranchListSearchLikeBranchName(Event event) throws Exception {
		FDUtils.logEventDebug(this, event);

		// if not empty
		if (!tb_Branch_Name.getValue().isEmpty()) {
			checkbox_Branch_ShowAll.setChecked(false); // unCheck

			if (getBranchListCtrl().getBinder() != null) {

				// ++ create a searchObject and init sorting ++//
				HibernateSearchObject<Branche> searchObjBranch = new HibernateSearchObject<Branche>(Branche.class, getBranchListCtrl().getCountRows());
				searchObjBranch.addFilter(new Filter("braBezeichnung", "%" + tb_Branch_Name.getValue() + "%", Filter.OP_ILIKE));
				searchObjBranch.addSort("braBezeichnung", false);

				// Change the BindingListModel.
				getBranchListCtrl().getPagedBindingListWrapper().setSearchObject(searchObjBranch);

				// get the current Tab for later checking if we must change it
				Tab currentTab = tabbox_BranchMain.getSelectedTab();

				// check if the tab is one of the Detail tabs. If so do not
				// change the selection of it
				if (!currentTab.equals(tabBranchList)) {
					tabBranchList.setSelected(true);
				} else {
					currentTab.setSelected(true);
				}
			}
		}
	}

	/**
	 * Removes all Filters from the branch list and shows all. <br>
	 * 
	 * @param event
	 */
	private void doBranchListShowAll(Event event) {
		FDUtils.logEventDebug(this, event);

		// empty the text search boxes
		tb_Branch_Name.setValue(""); // clear

		if (getBranchListCtrl().getBinder() != null) {
			getBranchListCtrl().getPagedBindingListWrapper().clearFilters();

			// get the current Tab for later checking if we must change it
			Tab currentTab = tabbox_BranchMain.getSelectedTab();

			// check if the tab is one of the Detail tabs. If so do not
			// change the selection of it
			if (!currentTab.equals(tabBranchList)) {
				tabBranchList.setSelected(true);
			} else {
				currentTab.setSelected(true);
			}
		}
	}

	/**
	 * Cancels the current action and resets the values and buttons.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	private void doCancel(Event event) throws InterruptedException {
		FDUtils.logEventDebug(this, event);

		// reset to the original object
		doResetToInitValues();

		// check first, if the tabs are created
		if (getBranchDetailCtrl().getBinder() != null) {

			// refresh all dataBinder related controllers/components
			getBranchDetailCtrl().getBinder().loadAll();

			// set edit-Mode
			getBranchDetailCtrl().doReadOnlyMode(true);

			btnCtrlBranch.setInitEdit();
		}
	}

	/**
	 * Sets all UI-components to writable-mode. Sets the buttons to edit-Mode.
	 * Checks, first if the needed tabs are created. If not, than create it by a
	 * Events.sendEvent()
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	private void doEdit(Event event) {
		FDUtils.logEventDebug(this, event);

		// get the current Tab for later checking if we must change it
		Tab currentTab = tabbox_BranchMain.getSelectedTab();

		// check first, if the tabs are created, if not than create it
		if (getBranchDetailCtrl() == null) {
			Events.sendEvent(new Event("onSelect", tabBranchDetail, null));
			// if we work with spring beanCreation than we must check a little
			// bit deeper, because the Controller are preCreated ?
		} else if (getBranchDetailCtrl().getBinder() == null) {
			Events.sendEvent(new Event("onSelect", tabBranchDetail, null));
		}

		// check if the tab is one of the Detail tabs. If so do not change the
		// selection of it
		if (!currentTab.equals(tabBranchDetail)) {
			tabBranchDetail.setSelected(true);
		} else {
			currentTab.setSelected(true);
		}

		getBranchDetailCtrl().getBinder().loadAll();

		// remember the old vars
		doStoreInitValues();

		btnCtrlBranch.setBtnStatus_Edit();

		getBranchDetailCtrl().doReadOnlyMode(false);
		// set focus
		getBranchDetailCtrl().txtb_BranchText.focus();
	}

	/**
	 * Deletes the selected Bean from the DB.
	 * 
	 * @param event
	 * @throws InterruptedException
	 * @throws InterruptedException
	 */
	private void doDelete(Event event) throws InterruptedException {
		FDUtils.logEventDebug(this, event);

		// check first, if the tabs are created, if not than create them
		if (getBranchDetailCtrl().getBinder() == null) {
			Events.sendEvent(new Event("onSelect", tabBranchDetail, null));
		}

		// check first, if the tabs are created
		if (getBranchDetailCtrl().getBinder() == null) {
			return;
		}

		final Branche aBranche = getSelectedBranche();
		if (aBranche != null) {

			// Show a confirm box
			String msg = Labels.getLabel("message.Question.Are_you_sure_to_delete_this_record") + "\n\n --> " + aBranche.getBraBezeichnung();
			String title = Labels.getLabel("message.Deleting.Record");

			MultiLineMessageBox.doSetTemplate();
			if (MultiLineMessageBox.show(msg, title, Messagebox.YES | Messagebox.NO, Messagebox.QUESTION, true, new EventListener() {
				public void onEvent(Event evt) {
					switch (((Integer) evt.getData()).intValue()) {
					case MultiLineMessageBox.YES:
						deleteBean();
						break; // 
					case MultiLineMessageBox.NO:
						break; // 
					}
				}

				private void deleteBean() {
					// delete from database
					getBrancheService().delete(aBranche);
				}

			}

			) == MultiLineMessageBox.YES) {
			}

		}

		btnCtrlBranch.setInitEdit();

		setSelectedBranche(null);
		// refresh the list
		getBranchListCtrl().doFillListbox();

		// refresh all dataBinder related controllers
		getBranchDetailCtrl().getBinder().loadAll();
	}

	/**
	 * Saves all involved Beans to the DB.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	private void doSave(Event event) throws InterruptedException {
		FDUtils.logEventDebug(this, event);

		// save all components data in the several tabs to the bean
		getBranchDetailCtrl().getBinder().saveAll();

		try {
			// save it to database
			getBrancheService().saveOrUpdate(getBranchDetailCtrl().getBranche());
			// if saving is successfully than actualize the beans as
			// origins.
			doStoreInitValues();
			// refresh the list
			getBranchListCtrl().doFillListbox();
			// later refresh StatusBar
			Events.postEvent("onSelect", getBranchListCtrl().getListBoxBranch(), getSelectedBranche());

			// show the objects data in the statusBar
			String str = getSelectedBranche().getBraBezeichnung();
			EventQueues.lookup("selectedObjectEventQueue", EventQueues.DESKTOP, true).publish(new Event("onChangeSelectedObject", null, str));

		} catch (DataAccessException e) {
			String message = e.getMessage();
			String title = Labels.getLabel("message.Error");
			MultiLineMessageBox.doSetTemplate();
			MultiLineMessageBox.show(message, title, MultiLineMessageBox.OK, "ERROR", true);

			// Reset to init values
			doResetToInitValues();

			return;

		} finally {
			btnCtrlBranch.setInitEdit();
			getBranchDetailCtrl().doReadOnlyMode(true);
		}
	}

	/**
	 * Sets all UI-components to writable-mode. Stores the current Beans as
	 * originBeans and get new Objects from the backend.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	private void doNew(Event event) {
		FDUtils.logEventDebug(this, event);

		// check first, if the tabs are created
		if (getBranchDetailCtrl() == null) {
			Events.sendEvent(new Event("onSelect", tabBranchDetail, null));
			// if we work with spring beanCreation than we must check a little
			// bit deeper, because the Controller are preCreated ?
		} else if (getBranchDetailCtrl().getBinder() == null) {
			Events.sendEvent(new Event("onSelect", tabBranchDetail, null));
		}

		// remember the current object
		doStoreInitValues();

		/** !!! DO NOT BREAK THE TIERS !!! */
		// We don't create a new DomainObject() in the frontend.
		// We GET it from the backend.
		Branche aBranche = getBrancheService().getNewBranche();

		// set the beans in the related databinded controllers
		getBranchDetailCtrl().setBranche(aBranche);
		getBranchDetailCtrl().setSelectedBranche(aBranche);

		// Refresh the binding mechanism
		getBranchDetailCtrl().setSelectedBranche(getSelectedBranche());
		getBranchDetailCtrl().getBinder().loadAll();

		// set edit-Mode
		getBranchDetailCtrl().doReadOnlyMode(false);

		// set the ButtonStatus to New-Mode
		btnCtrlBranch.setInitNew();

		tabBranchDetail.setSelected(true);
		// set focus
		getBranchDetailCtrl().txtb_BranchText.focus();

	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++ //
	// ++++++++++++++++++++ Helpers ++++++++++++++++++++ //
	// +++++++++++++++++++++++++++++++++++++++++++++++++ //

	/**
	 * Resizes the container from the selected Tab.
	 * 
	 * @param event
	 */
	private void doResizeSelectedTab(Event event) {
		FDUtils.logEventDebug(this, event);

		if (tabbox_BranchMain.getSelectedTab() == tabBranchDetail) {
			getBranchDetailCtrl().doFitSize(event);
		} else if (tabbox_BranchMain.getSelectedTab() == tabBranchList) {
			// resize and fill Listbox new
			getBranchListCtrl().doFillListbox();
		}
	}

	/**
	 * Opens the help screen for the current module.
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	private void doHelp(Event event) throws InterruptedException {

		FDUtils.doShowNotImplementedMessage();

		// we stop the propagation of the event, because zk will call ALL events
		// with the same name in the namespace and 'btnHelp' is a standard
		// button in this application and can often appears.
		// Events.getRealOrigin((ForwardEvent) event).stopPropagation();
		event.stopPropagation();
	}

	/**
	 * Saves the selected object's current properties. We can get them back if a
	 * modification is canceled.
	 * 
	 * @see doResetToInitValues()
	 */
	public void doStoreInitValues() {

		if (getSelectedBranche() != null) {

			try {
				setOriginalBranche((Branche) org.apache.commons.beanutils.BeanUtils.cloneBean(getSelectedBranche()));
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Reset the selected object to its origin property values.
	 * 
	 * @see doStoreInitValues()
	 * 
	 */
	public void doResetToInitValues() {

		if (getOriginalBranche() != null) {

			try {
				getBranchDetailCtrl().setBranche((Branche) org.apache.commons.beanutils.BeanUtils.cloneBean(getOriginalBranche()));
				setSelectedBranche((Branche) org.apache.commons.beanutils.BeanUtils.cloneBean(getOriginalBranche()));
				// TODO Bug in DataBinder??
				windowBranchMain.invalidate();
				getBranchDetailCtrl().windowBranchDetail.invalidate();

			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * User rights check. <br>
	 * Only components are set visible=true if the logged-in <br>
	 * user have the right for it. <br>
	 * 
	 * The rights are get from the spring framework users grantedAuthority(). A
	 * right is only a string. <br>
	 */
	// TODO move it to the zul-file
	private void doCheckRights() {

		UserWorkspace workspace = getUserWorkspace();

		btnHelp.setVisible(workspace.isAllowed("button_BranchMain_btnHelp"));
		btnNew.setVisible(workspace.isAllowed("button_BranchMain_btnNew"));
		btnEdit.setVisible(workspace.isAllowed("button_BranchMain_btnEdit"));
		btnDelete.setVisible(workspace.isAllowed("button_BranchMain_btnDelete"));
		btnSave.setVisible(workspace.isAllowed("button_BranchMain_btnSave"));
		btnClose.setVisible(workspace.isAllowed("button_BranchMain_btnClose"));

		button_BranchMain_PrintBranches.setVisible(workspace.isAllowed("button_BranchMain_PrintBranches"));
		button_BranchMain_Search_BranchName.setVisible(workspace.isAllowed("button_BranchMain_Search_BranchName"));

	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++ //
	// ++++++++++++++++ Setter/Getter ++++++++++++++++++ //
	// +++++++++++++++++++++++++++++++++++++++++++++++++ //

	public void setBranche(Branche branche) {
		this.branche = branche;
	}

	public Branche getBranche() {
		return branche;
	}

	public void setOriginalBranche(Branche originalBranche) {
		this.originalBranche = originalBranche;
	}

	public Branche getOriginalBranche() {
		return originalBranche;
	}

	public void setSelectedBranche(Branche selectedBranche) {
		this.selectedBranche = selectedBranche;
	}

	public Branche getSelectedBranche() {
		return selectedBranche;
	}

	public void setBranches(BindingListModelList branches) {
		this.branches = branches;
	}

	public BindingListModelList getBranches() {
		return branches;
	}

	public BrancheService getBrancheService() {
		return brancheService;
	}

	public void setBrancheService(BrancheService brancheService) {
		this.brancheService = brancheService;
	}

	public void setBranchListCtrl(BranchListCtrl branchListCtrl) {
		this.branchListCtrl = branchListCtrl;
	}

	public BranchListCtrl getBranchListCtrl() {
		return branchListCtrl;
	}

	public void setBranchDetailCtrl(BranchDetailCtrl branchDetailCtrl) {
		this.branchDetailCtrl = branchDetailCtrl;
	}

	public BranchDetailCtrl getBranchDetailCtrl() {
		return branchDetailCtrl;
	}

}