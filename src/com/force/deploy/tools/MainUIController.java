/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.force.deploy.tools;

import com.force.deploy.tools.utils.DeployResult;
import com.force.deploy.tools.utils.Project;
import com.force.deploy.tools.utils.Serializer;
import com.sforce.soap.metadata.AsyncResult;
import com.sforce.soap.metadata.DeleteResult;
import com.sforce.soap.metadata.DeployOptions;
import com.sforce.soap.metadata.DeployStatus;
import com.sforce.soap.metadata.DescribeMetadataObject;
import com.sforce.soap.metadata.DescribeMetadataResult;
import com.sforce.soap.metadata.FileProperties;
import com.sforce.soap.metadata.ListMetadataQuery;
import com.sforce.soap.metadata.Metadata;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.PackageTypeMembers;
import com.sforce.soap.metadata.ReadResult;
import com.sforce.soap.metadata.RetrieveMessage;
import com.sforce.soap.metadata.RetrieveRequest;
import com.sforce.soap.metadata.RetrieveResult;
import com.sforce.soap.metadata.SaveResult;
import com.sforce.soap.partner.LoginResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.metadata.Package;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 *
 * @author Daniel
 */
public class MainUIController implements Initializable {

    @FXML
    private ListView<String> projects;
    @FXML
    private TreeView<String> metaSource;
    @FXML
    private TreeView<String> metaTarget;
    @FXML
    private ComboBox<String> target;
    @FXML
    private Label source;
    @FXML
    private Label details;
    @FXML
    private TableView<DeployResult> results;
    @FXML
    private MenuButton loadMeta;

    private static final Logger log = Logger.getLogger(MainUIController.class.getName());

    public static ObservableList<String> projectItems = FXCollections.observableArrayList();
    public static Project selectedProject;

    public static ObservableList<DeployResult> deployResults = FXCollections.observableArrayList();

    private PartnerConnection part;
    private MetadataConnection meta;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initProjects();
        initMetaSource();
        initMetaTarget();
        initResults();
    }

    @FXML
    private void btnCreateAction(ActionEvent event) {
        deployResults.clear();

        HashMap<String, Project> saved = (HashMap<String, Project>) Serializer.deserialize(Project.PROJECT_REPOSITORY);

        MetadataConnection sourceMetaConn = getMetaConnection(saved.get(source.getText()));
        MetadataConnection targetMetaConn = getMetaConnection(saved.get(target.getSelectionModel().getSelectedItem()));

        for (TreeItem<String> parent : metaTarget.getRoot().getChildren()) {
            String parentValue = parent.getValue();
            if (parentValue.startsWith("Apex")) {
                processApex(parentValue, parent.getChildren(), sourceMetaConn, targetMetaConn);
            } else {
                processNormal(parentValue, parent.getChildren(), sourceMetaConn, targetMetaConn);
            }
        }
    }

    @FXML
    private void btnDeleteAction(ActionEvent event) {
        try {
            deployResults.clear();

            HashMap<String, Project> saved = (HashMap<String, Project>) Serializer.deserialize(Project.PROJECT_REPOSITORY);

            MetadataConnection targetMetaConn = getMetaConnection(saved.get(target.getSelectionModel().getSelectedItem()));

            for (TreeItem<String> parent : metaTarget.getRoot().getChildren()) {

                Set<String> children = new TreeSet<>();
                for (TreeItem<String> child : parent.getChildren()) {
                    children.add(child.getValue());
                }

                DeleteResult[] saveResult = targetMetaConn.deleteMetadata(parent.getValue(), children.toArray(new String[]{}));

                List<DeployResult> resultsList = new ArrayList<>();
                for (DeleteResult sr : saveResult) {
                    resultsList.add(new DeployResult(sr));
                }

                deployResults.addAll(resultsList);
            }
        } catch (ConnectionException ex) {
            log.log(Level.INFO, null, ex);
        }
    }

    @FXML
    private void btnAddAction(ActionEvent event) {
        try {
            selectedProject = null;
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Credentials.fxml"));
            Scene scene = new Scene(root);
            final Stage dialog = new Stage();
            dialog.setTitle("Force.com Project");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException ex) {
            log.log(Level.INFO, null, ex);
        }
    }

    private void initResults() {
        results.setEditable(true);
        TableColumn<DeployResult, String> c1 = new TableColumn<>("Metadata Component");
        c1.setCellValueFactory(new PropertyValueFactory<>("component"));
        c1.setMinWidth(200);
        TableColumn<DeployResult, String> c2 = new TableColumn<>("Status Code");
        c2.setCellValueFactory(new PropertyValueFactory<>("statusCode"));
        c2.setMinWidth(300);
        TableColumn<DeployResult, String> c3 = new TableColumn<>("Message");
        c3.setCellValueFactory(new PropertyValueFactory<>("message"));
        c3.setMinWidth(700);
        TableColumn<DeployResult, String> c4 = new TableColumn<>("Success");
        c4.setCellValueFactory(new PropertyValueFactory<>("success"));
        c4.setMinWidth(73);
        results.getColumns().addAll(c1, c2, c3, c4);
        results.setItems(deployResults);
    }

    private void initLoadMeta() {
        loadMeta.getItems().clear();
        prepareLoad();

        Set<String> thingsToLoad = new TreeSet<>();
        DescribeMetadataResult desc;
        try {
            desc = meta.describeMetadata(30.0);
            for (DescribeMetadataObject obj : desc.getMetadataObjects()) {
                thingsToLoad.add(obj.getXmlName());
                String[] children = obj.getChildXmlNames();
                if (children != null && children.length > 0) {
                    for (String child : Arrays.asList(children)) {
                        if (child != null) {
                            thingsToLoad.add(child);
                        }
                    }
                }
            }
        } catch (ConnectionException ex) {
            log.log(Level.INFO, null, ex);
        }

        List<MenuItem> btnLoadItems = new ArrayList<>();
        final Map<String, String> subFolderItems = new TreeMap<>();
        for (String f : "Dashboard,Document,Report".split(",")) {
            subFolderItems.put(f, f + "Folder");
        }
        subFolderItems.put("EmailTemplate", "EmailFolder");

        for (String folder : thingsToLoad) {
            MenuItem item = new MenuItem(folder);
            item.setOnAction(new EventHandler<ActionEvent>() {

                @Override
                public void handle(ActionEvent event) {
                    try {

                        HashMap<String, TreeSet<String>> props = new HashMap<>();

                        String localFolder = folder;
                        if (subFolderItems.containsKey(folder)) {
                            localFolder = subFolderItems.get(folder);
                        }

                        ListMetadataQuery query = new ListMetadataQuery();
                        query.setType(localFolder);

                        FileProperties[] fp = meta.listMetadata(new ListMetadataQuery[]{query}, 29.0);
                        props.putAll(buildComponents(fp));

                        TreeItem<String> rootItem = new TreeItem<>(localFolder);
                        List<TreeItem<String>> toAdd = new ArrayList<>();
                        for (String type : props.keySet()) {
                            if (subFolderItems.containsKey(folder)) {
                                for (String component : props.get(type)) {
                                    ListMetadataQuery q = new ListMetadataQuery();
                                    q.setFolder(component);
                                    q.setType(folder);
                                    fp = meta.listMetadata(new ListMetadataQuery[]{q}, 29.0);
                                    if (fp.length > 0) {
                                        TreeItem<String> item = new TreeItem<>(component);
                                        List<TreeItem<String>> subItems = new ArrayList<>();
                                        for (String subComponent : buildSubComponents(fp).get(component)) {
                                            TreeItem<String> treeSubItem = new TreeItem<>(subComponent);
                                            subItems.add(treeSubItem);
                                        }
                                        item.getChildren().addAll(subItems);
                                        toAdd.add(item);
                                    }
                                }
                            } else {
                                for (String component : props.get(type)) {
                                    toAdd.add(new TreeItem<>(component));
                                }
                            }
                        }
                        metaSource.getRoot().getChildren().clear();
                        if (!toAdd.isEmpty()) {
                            rootItem.setExpanded(true);
                            rootItem.getChildren().addAll(toAdd);
                            metaSource.getRoot().getChildren().add(rootItem);
                        }
                    } catch (ConnectionException ex) {
                        log.log(Level.INFO, null, ex);
                    }
                }
            });
            btnLoadItems.add(item);
        }
        loadMeta.getItems().addAll(btnLoadItems);
    }

    private void initMetaTarget() {
        metaTarget.setRoot(new TreeItem<>("Target Metadata"));
        metaTarget.setShowRoot(false);

        metaTarget.setOnMouseClicked(new EventHandler<MouseEvent>() {

            @Override
            public void handle(MouseEvent event) {
                if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                    TreeItem<String> item = metaTarget.getSelectionModel().getSelectedItem();
                    if (item.isLeaf()) {
                        TreeItem<String> parent = item.getParent();
                        parent.getChildren().remove(item);
                        if (parent.getChildren().isEmpty()) {
                            metaTarget.getRoot().getChildren().remove(parent);
                        }
                    }
                }
            }
        });
    }

    private void initMetaSource() {
        metaSource.setRoot(new TreeItem<>("Source Metadata"));
        metaSource.setShowRoot(false);

        metaSource.setOnMouseClicked(new EventHandler<MouseEvent>() {

            @Override
            public void handle(MouseEvent event) {
                if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                    TreeItem<String> item = metaSource.getSelectionModel().getSelectedItem();
                    String itemVal = item.getValue();
                    if (item.isLeaf()) {
                        TreeItem<String> parent = item.getParent();
                        String parentVal = parent.getValue();

                        TreeItem<String> targetRoot = metaTarget.getRoot();
                        boolean parentFound = false;
                        for (TreeItem<String> targetRootChild : targetRoot.getChildren()) {
                            if (targetRootChild.getValue().equals(parentVal)) {
                                parentFound = true;
                                boolean itemFound = false;
                                for (TreeItem<String> targetChildCell : targetRootChild.getChildren()) {
                                    if (targetChildCell.getValue().equals(itemVal)) {
                                        itemFound = true;
                                        break;
                                    }
                                }
                                if (!itemFound) {
                                    targetRootChild.getChildren().add(new TreeItem<>(itemVal));
                                }
                                break;
                            }
                        }
                        if (!parentFound) {
                            TreeItem<String> targetParent = new TreeItem<>(parentVal);
                            targetParent.setExpanded(true);
                            targetParent.getChildren().add(new TreeItem<>(itemVal));
                            targetRoot.getChildren().add(targetParent);
                        }
                    }
                }
            }
        });
    }

    private void initProjects() {
        projects.setItems(projectItems);
        target.setItems(projectItems);
        HashMap<String, Project> saved = (HashMap<String, Project>) Serializer.deserialize(Project.PROJECT_REPOSITORY);
        if (saved != null) {
            projectItems.addAll(saved.keySet());
        }
        projects.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                try {
                    String name = projects.getSelectionModel().getSelectedItem();
                    HashMap<String, Project> saved = (HashMap<String, Project>) Serializer.deserialize(Project.PROJECT_REPOSITORY);
                    selectedProject = saved.get(name);
                    source.setText(name);
                    if (event.getButton().equals(MouseButton.PRIMARY)) {
                        if (event.getClickCount() == 1) {
                            initLoadMeta();
                        }
                        if (event.getClickCount() == 2) {
                            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Credentials.fxml"));
                            Scene scene = new Scene(root);
                            final Stage dialog = new Stage();
                            dialog.setTitle("Force.com Project");
                            dialog.initModality(Modality.APPLICATION_MODAL);
                            dialog.initStyle(StageStyle.UTILITY);
                            dialog.setScene(scene);
                            dialog.showAndWait();
                        }
                    }
                } catch (IOException ex) {
                    log.log(Level.INFO, null, ex);
                }
            }
        });

        ContextMenu contextMenu = new ContextMenu();
        MenuItem openWebLink = new MenuItem("Open Web Link");
        openWebLink.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent event) {
                try {
                    Project project = selectedProject;

                    String endPoint = getEndpoint(project.environment, project.instance);

                    ConnectorConfig partConfig = new ConnectorConfig();
                    partConfig.setAuthEndpoint(endPoint);
                    partConfig.setServiceEndpoint(endPoint);
                    partConfig.setManualLogin(true);

                    part = new PartnerConnection(partConfig);
                    LoginResult result = part.login(project.username, project.password + project.securityToken);
                    String loginLink = result.getServerUrl();
                    loginLink = loginLink.substring(0, loginLink.indexOf("/", 8)) + "/secur/frontdoor.jsp?sid=" + result.getSessionId();

                    Desktop.getDesktop().browse(new URI(loginLink));
                } catch (ConnectionException | IOException | URISyntaxException ex) {
                    log.log(Level.INFO, null, ex);
                }
            }
        });
        MenuItem copyWebLink = new MenuItem("Copy Web Link");
        copyWebLink.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent event) {
                try {
                    Project project = selectedProject;

                    String endPoint = getEndpoint(project.environment, project.instance);

                    ConnectorConfig partConfig = new ConnectorConfig();
                    partConfig.setAuthEndpoint(endPoint);
                    partConfig.setServiceEndpoint(endPoint);
                    partConfig.setManualLogin(true);

                    part = new PartnerConnection(partConfig);
                    LoginResult result = part.login(project.username, project.password + project.securityToken);
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    String loginLink = result.getServerUrl();
                    loginLink = loginLink.substring(0, loginLink.indexOf("/", 8)) + "/secur/frontdoor.jsp?sid=" + result.getSessionId();
                    content.putString(loginLink);
                    clipboard.setContent(content);
                } catch (ConnectionException ex) {
                    log.log(Level.INFO, null, ex);
                }
            }
        });
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent event) {
                projectItems.remove(selectedProject.name);
                HashMap<String, Project> saved = (HashMap<String, Project>) Serializer.deserialize(Project.PROJECT_REPOSITORY);
                if (saved != null) {
                    saved.remove(selectedProject.name);
                }
                Serializer.serialize(saved, Project.PROJECT_REPOSITORY);
            }
        });
        contextMenu.getItems().addAll(openWebLink, copyWebLink, delete);

        projects.setContextMenu(contextMenu);
    }

    private void prepareLoad() {
        try {
            Project project = selectedProject;

            String endPoint = getEndpoint(project.environment, project.instance);

            ConnectorConfig config = new ConnectorConfig();
            config.setAuthEndpoint(endPoint);
            config.setServiceEndpoint(endPoint);
            config.setManualLogin(true);

            part = new PartnerConnection(config);
            LoginResult result = part.login(project.username, project.password + project.securityToken);
            config.setServiceEndpoint(result.getServerUrl());
            config.setSessionId(result.getSessionId());
            part = new PartnerConnection(config);

            config.setServiceEndpoint(result.getMetadataServerUrl());
            config.setSessionId(result.getSessionId());
            meta = new MetadataConnection(config);
        } catch (ConnectionException ex) {
            log.log(Level.INFO, null, ex);
        }
    }

    private String getEndpoint(String environment, String instance) {
        if (null != environment) {
            switch (environment) {
                case "Production/Developer Edition":
                    return "https://login.salesforce.com/services/Soap/u/29.0";
                case "Sandbox":
                    return "https://test.salesforce.com/services/Soap/u/29.0";
                case "Pre-release":
                    return "";
                case "Other (Specify)":
                    return instance;
            }
        }
        return "";
    }

    public static HashMap<String, TreeSet<String>> buildComponents(FileProperties[] props) {
        HashMap<String, TreeSet<String>> ret = new HashMap<>();
        for (FileProperties prop : props) {
            String type = prop.getType();

            if (!ret.containsKey(type)) {
                ret.put(type, new TreeSet<>());
            }

            ret.get(type).add(prop.getFullName());
        }
        return ret;
    }

    public static HashMap<String, TreeSet<String>> buildSubComponents(FileProperties[] props) {
        HashMap<String, TreeSet<String>> ret = new HashMap<>();
        for (FileProperties prop : props) {
            String[] parts = prop.getFullName().split("/");
            String type = parts[0];
            String value = parts[1];

            if (!ret.containsKey(type)) {
                ret.put(type, new TreeSet<>());
            }

            ret.get(type).add(value);
        }
        return ret;
    }

    private MetadataConnection getMetaConnection(Project project) {
        try {
            String endPoint = getEndpoint(project.environment, project.instance);

            ConnectorConfig config = new ConnectorConfig();
            config.setAuthEndpoint(endPoint);
            config.setServiceEndpoint(endPoint);
            config.setManualLogin(true);

            PartnerConnection pc = new PartnerConnection(config);
            LoginResult result = pc.login(project.username, project.password + project.securityToken);

            config.setServiceEndpoint(result.getMetadataServerUrl());
            config.setSessionId(result.getSessionId());
            return new MetadataConnection(config);
        } catch (ConnectionException ex) {
            log.log(Level.INFO, null, ex);
        }
        return null;
    }

    private void processNormal(String parentValue, List<TreeItem<String>> parentChildren, MetadataConnection sourceMetaConn, MetadataConnection targetMetaConn) {
        try {
            String prefix = "";
            ListMetadataQuery query = new ListMetadataQuery();
            query.setType(parentValue);
            FileProperties[] props = sourceMetaConn.listMetadata(new ListMetadataQuery[]{query}, 29.0);
            prefix = props[0].getNamespacePrefix();
            if (!"".equals(prefix)) {
                prefix += "__";
            }

            List<String> children = new ArrayList<>();
            for (TreeItem<String> child : parentChildren) {
                String v = child.getValue();
                if (!"".equals(prefix) && v.contains(".")) {
                    String[] parts = v.split("\\.");
                    if (parts[0].endsWith("__c")) {
                        parts[0] = prefix + parts[0];
                    }
                    if (parts[1].endsWith("__c")) {
                        parts[1] = prefix + parts[1];
                    }
                    v = parts[0] + "." + parts[1];
                }
                children.add(v);
            }

            ReadResult readResult = sourceMetaConn.readMetadata(parentValue, children.toArray(new String[]{}));
            List<Metadata> metadata = new ArrayList<>();
            List<DeployResult> resultsList = new ArrayList<>();
            int i = 0;
            for (Metadata m : readResult.getRecords()) {
                if (m != null) {
                    m.setFullName(m.getFullName().replace(prefix, ""));
                    metadata.add(m);
                } else {
                    log.log(Level.WARNING, "Metadata not read! ({0} not created!)", children.get(i));
                    resultsList.add(new DeployResult(children.get(i), "Metadata not read!"));
                }
                i++;
            }

            if (!metadata.isEmpty()) {
                SaveResult[] saveResult = targetMetaConn.createMetadata(metadata.toArray(new Metadata[]{}));

                for (SaveResult sr : saveResult) {
                    resultsList.add(new DeployResult(sr));
                }
            }

            deployResults.addAll(resultsList);
        } catch (ConnectionException ex) {
            log.log(Level.SEVERE, null, ex);
        }
    }

    private void processApex(String parentValue, List<TreeItem<String>> parentChildren, MetadataConnection sourceMetaConn, MetadataConnection targetMetaConn) {
        try {
            // one second in milliseconds
            final long ONE_SECOND = 1000;
            // maximum number of attempts to retrieve the results
            final int MAX_NUM_POLL_REQUESTS = 50;
            final double API_VERSION = 31.0;

            /*String prefix = "";
             ListMetadataQuery query = new ListMetadataQuery();
             query.setType(parentValue);
             FileProperties[] props = sourceMetaConn.listMetadata(new ListMetadataQuery[]{query}, 29.0);
             prefix = props[0].getNamespacePrefix();
             if (!"".equals(prefix)) {
             prefix += "__";
             }*/
            List<String> children = new ArrayList<>();
            for (TreeItem<String> child : parentChildren) {
                String v = child.getValue();
                /*if (!"".equals(prefix) && v.contains(".")) {
                 String[] parts = v.split("\\.");
                 if (parts[0].endsWith("__c")) {
                 parts[0] = prefix + parts[0];
                 }
                 if (parts[1].endsWith("__c")) {
                 parts[1] = prefix + parts[1];
                 }
                 v = parts[0] + "." + parts[1];
                 }*/
                children.add(v);
            }

            PackageTypeMembers ptm = new PackageTypeMembers();
            ptm.setName(parentValue);
            ptm.setMembers(children.toArray(new String[]{}));

            Package p = new Package();
            p.setTypes(new PackageTypeMembers[]{ptm});
            p.setVersion(API_VERSION + "");
            RetrieveRequest request = new RetrieveRequest();
            request.setUnpackaged(p);

            // Start the retrieve operation
            AsyncResult asyncResult = sourceMetaConn.retrieve(request);
            log.info("asyncResult " + asyncResult.toString());
            String asyncResultId = asyncResult.getId();

            // Wait for the retrieve to complete
            int poll = 0;
            long waitTimeMilliSecs = ONE_SECOND;
            RetrieveResult result = null;

            deployResults.clear();
            
            do {
                Thread.sleep(waitTimeMilliSecs);
                // Double the wait time for the next iteration
                waitTimeMilliSecs *= 2;
                if (poll++ > MAX_NUM_POLL_REQUESTS) {
                    throw new Exception("Request timed out. If this is a large set "
                            + "of metadata components, check that the time allowed "
                            + "by MAX_NUM_POLL_REQUESTS is sufficient.");
                }
                result = sourceMetaConn.checkRetrieveStatus(asyncResultId);

                StringBuilder buf = new StringBuilder();
                if (result.getMessages() != null) {
                    for (RetrieveMessage rm : result.getMessages()) {
                        buf.append(rm.getFileName() + " - " + rm.getProblem());
                    }
                }
                if (buf.length() > 0) {
                    System.out.println("Retrieve warnings:\n" + buf);
                    deployResults.add(new DeployResult(parentValue, buf.toString()));
                }
                if (result.getZipFile() != null) {
                    break;
                }
            } while (true);

//            System.out.println("Writing results to zip file");
//            ByteArrayInputStream bais = new ByteArrayInputStream(result.getZipFile());
//            File resultsFile = new File("./tmp/retrieveResults.zip");
//            try (FileOutputStream os = new FileOutputStream(resultsFile)) {
//                ReadableByteChannel src = Channels.newChannel(bais);
//                FileChannel dest = os.getChannel();
//                copy(src, dest);
//                System.out.println("Results written to " + resultsFile.getAbsolutePath());
//            }
            DeployOptions deployOptions = new DeployOptions();
            deployOptions.setPerformRetrieve(false);
            deployOptions.setRollbackOnError(true);
            asyncResult = targetMetaConn.deploy(result.getZipFile(), deployOptions);
            asyncResultId = asyncResult.getId();

            com.sforce.soap.metadata.DeployResult deployResult = null;
            boolean fetchDetails;
            waitTimeMilliSecs = ONE_SECOND;
            poll = 0;
            do {
                Thread.sleep(waitTimeMilliSecs);
                // double the wait time for the next iteration
                waitTimeMilliSecs *= 2;
                if (poll++ > MAX_NUM_POLL_REQUESTS) {
                    
                    throw new Exception("Request timed out. If this is a large set "
                            + "of metadata components, check that the time allowed by "
                            + "MAX_NUM_POLL_REQUESTS is sufficient.");
                }
                // Fetch in-progress details once for every 3 polls
                fetchDetails = (poll % 3 == 0);
                deployResult = targetMetaConn.checkDeployStatus(asyncResultId, fetchDetails);
                deployResults.add(new DeployResult(parentValue, "Status: " + deployResult.getStatus()));
                System.out.println("Status is: " + deployResult.getStatus());
            } while (!deployResult.isDone());
            if (!deployResult.isSuccess() && deployResult.getErrorStatusCode() != null) {
                throw new Exception(deployResult.getErrorStatusCode() + " msg: "
                        + deployResult.getErrorMessage());
            }
            if (!deployResult.isSuccess()) {
                throw new Exception("The files were not successfully deployed. " + deployResult.getErrorStatusCode() + " msg: "
                        + deployResult.getErrorMessage());
            }
            details.setText(String.format("Details:\nCompleted:%s\nErrors:%s\nDeployed:%s\nTotal:%s\nTest Errors:%s\nCompleted Tests:%s\nTests Total:%s", 
                    deployResult.getCompletedDate(), deployResult.getNumberComponentErrors(),
                    deployResult.getNumberComponentsDeployed(),
                    deployResult.getNumberComponentsTotal(),
                    deployResult.getNumberTestErrors(),
                    deployResult.getNumberTestsCompleted(),
                    deployResult.getNumberTestsTotal()));
//            System.out.println("The file " + ZIP_FILE + " was successfully deployed");
        } catch (Exception ex) {
            deployResults.add(new DeployResult(parentValue, ex.getMessage()));
            log.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Helper method to copy from a readable channel to a writable channel,
     * using an in-memory buffer.
     */
    private void copy(ReadableByteChannel src, WritableByteChannel dest)
            throws IOException {
        // Use an in-memory byte buffer
        ByteBuffer buffer = ByteBuffer.allocate(8092);
        while (src.read(buffer) != -1) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                dest.write(buffer);
            }
            buffer.clear();
        }
    }
}
