package console;

import static console.common.ContractClassFactory.getContractClass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.fisco.bcos.channel.client.Service;
import org.fisco.bcos.channel.handler.ChannelConnections;
import org.fisco.bcos.channel.handler.GroupChannelConnectionsConfig;
import org.fisco.bcos.web3j.crypto.Credentials;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.crypto.Keys;
import org.fisco.bcos.web3j.crypto.gm.GenCredential;
import org.fisco.bcos.web3j.precompile.cns.CnsInfo;
import org.fisco.bcos.web3j.precompile.cns.CnsService;
import org.fisco.bcos.web3j.precompile.common.PrecompiledCommon;
import org.fisco.bcos.web3j.precompile.config.SystemConfigSerivce;
import org.fisco.bcos.web3j.precompile.consensus.ConsensusService;
import org.fisco.bcos.web3j.precompile.permission.PermissionInfo;
import org.fisco.bcos.web3j.precompile.permission.PermissionService;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.channel.ChannelEthereumService;
import org.fisco.bcos.web3j.protocol.channel.ResponseExcepiton;
import org.fisco.bcos.web3j.protocol.core.DefaultBlockParameter;
import org.fisco.bcos.web3j.protocol.core.DefaultBlockParameterName;
import org.fisco.bcos.web3j.protocol.core.RemoteCall;
import org.fisco.bcos.web3j.tx.Contract;
import org.fisco.bcos.web3j.tx.gas.ContractGasProvider;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;
import org.fisco.bcos.web3j.utils.Numeric;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.alibaba.fastjson.JSONObject;

import console.common.ConsoleUtils;
import console.common.ContractClassFactory;
import console.common.HelpInfo;
import io.bretty.console.table.Alignment;
import io.bretty.console.table.ColumnFormatter;
import io.bretty.console.table.Table;

public class ConsoleImpl implements ConsoleFace {
	
	  private static ChannelEthereumService channelEthereumService;
    private static Web3j web3j = null;
    private ApplicationContext context;
    private static java.math.BigInteger gasPrice = new BigInteger("10");
    private static java.math.BigInteger gasLimit = new BigInteger("50000000");
    private ECKeyPair keyPair;
    private static Credentials credentials;
    private String contractAddress;
    private String contractName;
    private String contractVersion;
    private Class<?> contractClass;
    private RemoteCall<?> remoteCall;
    private String privateKey = "";
    public static int groupID;
    public static final int InvalidRequest = 40009;
    
    public void init(String[] args) {
    		context = new ClassPathXmlApplicationContext("classpath:applicationContext.xml");
    		Service service = context.getBean(Service.class);
        groupID = service.getGroupId();
        if (args.length < 2) {
            InputStream is = null;
            OutputStream os = null;
            try {
                // read private key from privateKey.properties
                Properties prop = new Properties();
                Resource keyResource = new ClassPathResource("privateKey.properties");
                if (!keyResource.exists()) {
                    File privateKeyDir = new File("conf/privateKey.properties");
                    privateKeyDir.createNewFile();
                    keyResource = new ClassPathResource("privateKey.properties");
                }
                is = keyResource.getInputStream();
                prop.load(is);
                privateKey = prop.getProperty("privateKey");
                is.close();
                if (privateKey == null) {
                    // save private key in privateKey.properties
                    keyPair = Keys.createEcKeyPair();
                    privateKey = keyPair.getPrivateKey().toString(16);
                    prop.setProperty("privateKey", privateKey);
                    os = new FileOutputStream(keyResource.getFile());
                    prop.store(os, "private key");
                    os.close();
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                close();
            }
        }
        switch (args.length) {
            case 0:
                break;
            case 1:
                groupID = setGroupID(args, groupID);
                break;
            default:
                groupID = setGroupID(args, groupID);
                privateKey = args[1];
                break;
        }
        try {
            credentials = GenCredential.create(privateKey);
        } catch (NumberFormatException e) {
            System.out.println("Please provide private key by hex format.");
            close();
        }
        service.setGroupId(groupID);
        try {
            service.run();
        } catch (Exception e) {
            System.out.println(
                    "Failed to connect to the node. Please check the node status and the console configruation.");
            close();
        }
        channelEthereumService = new ChannelEthereumService();
        channelEthereumService.setChannelService(service);
        channelEthereumService.setTimeout(60000);
        web3j = Web3j.build(channelEthereumService, groupID);
        try {
            web3j.getBlockNumber().sendForReturnString();
        } catch (ResponseExcepiton e) {
            if (e.getCode() == InvalidRequest) {
                System.out.println("Don't connect a removed node.");
            } else {
                System.out.println(e.getMessage());
            }
            close();
        } catch (Exception e) {
            System.out.println(
                    "Failed to connect to the node. Please check the node status and the console configruation.");
            close();
        }
    }

    private int setGroupID(String[] args, int groupID) {
        try {
            groupID = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Please provide groupID by integer format.");
            close();
        }
        return groupID;
    }
    
    @Override
    public void close() {
      try {
        if (channelEthereumService != null) {
            channelEthereumService.close();
        }
        System.exit(0);
    } catch (IOException e) {
        System.out.println(e.getMessage());
    }
    
    }
    @Override
    public void welcome() {
        ConsoleUtils.doubleLine();
        System.out.println("Welcome to FISCO BCOS console!");
        System.out.println("Type 'help' or 'h' for help. Type 'quit' or 'q' to quit console.");
        String logo =
                " ________ ______  ______   ______   ______       _______   ______   ______   ______  \n"
                        + "|        |      \\/      \\ /      \\ /      \\     |       \\ /      \\ /      \\ /      \\ \n"
                        + "| $$$$$$$$\\$$$$$|  $$$$$$|  $$$$$$|  $$$$$$\\    | $$$$$$$|  $$$$$$|  $$$$$$|  $$$$$$\\\n"
                        + "| $$__     | $$ | $$___\\$| $$   \\$| $$  | $$    | $$__/ $| $$   \\$| $$  | $| $$___\\$$\n"
                        + "| $$  \\    | $$  \\$$    \\| $$     | $$  | $$    | $$    $| $$     | $$  | $$\\$$    \\ \n"
                        + "| $$$$$    | $$  _\\$$$$$$| $$   __| $$  | $$    | $$$$$$$| $$   __| $$  | $$_\\$$$$$$\\\n"
                        + "| $$      _| $$_|  \\__| $| $$__/  | $$__/ $$    | $$__/ $| $$__/  | $$__/ $|  \\__| $$\n"
                        + "| $$     |   $$ \\\\$$    $$\\$$    $$\\$$    $$    | $$    $$\\$$    $$\\$$    $$\\$$    $$\n"
                        + " \\$$      \\$$$$$$ \\$$$$$$  \\$$$$$$  \\$$$$$$      \\$$$$$$$  \\$$$$$$  \\$$$$$$  \\$$$$$$";
        System.out.println(logo);
        System.out.println();
        ConsoleUtils.doubleLine();
    }

    @Override
    public void help(String[] params) {
        if (HelpInfo.promptNoParams(params, "help")) {
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("help");
            return;
        }
        ConsoleUtils.singleLine();
        StringBuilder sb = new StringBuilder();
        sb.append("help(h)                                  Provide help information.\n");
        sb.append("switch(s)                                Switch to a specific group by group ID.\n");
        sb.append("getBlockNumber                           Query the number of most recent block.\n");
        sb.append("getPbftView                              Query the pbft view of node.\n");
        sb.append("getSealerList                            Query nodeId list for sealer nodes.\n");
        sb.append("getObserverList                          Query nodeId list for observer nodes.\n");
        sb.append(
                "getNodeIDList                            Query nodeId list for all connected nodes.\n");
        sb.append(
                "getGroupPeers                            Query nodeId list for sealer and observer nodes.\n");
        sb.append(
                "getPeers                                 Query peers currently connected to the client.\n");
        sb.append("getConsensusStatus                       Query consensus status.\n");
        sb.append("getSyncStatus                            Query sync status.\n");
        sb.append("getNodeVersion                           Query the current node version.\n");
        sb.append("getGroupList                             Query group list.\n");
        sb.append(
                "getBlockByHash                           Query information about a block by hash.\n");
        sb.append(
                "getBlockByNumber                         Query information about a block by block number.\n");
        sb.append("getBlockHashByNumber                     Query block hash by block number.\n");
        sb.append(
                "getTransactionByHash                     Query information about a transaction requested by transaction hash.\n");
        sb.append(
                "getTransactionByBlockHashAndIndex        Query information about a transaction by block hash and transaction index position.\n");
        sb.append(
                "getTransactionByBlockNumberAndIndex      Query information about a transaction by block number and transaction index position.\n");
        sb.append(
                "getTransactionReceipt                    Query the receipt of a transaction by transaction hash.\n");
        sb.append("getPendingTransactions                   Query pending transactions.\n");
        sb.append("getPendingTxSize                         Query pending transactions size.\n");
        sb.append("getCode                                  Query code at a given address.\n");
        sb.append("getTotalTransactionCount                 Query total transaction count.\n");
        sb.append("deploy                                   Deploy a contract on blockchain.\n");
        sb.append(
                "call                                     Call a contract by a function and paramters.\n");
        sb.append("deployByCNS                              Deploy a contract on blockchain by CNS.\n");
        sb.append(
                "callByCNS                                Call a contract by a function and paramters by CNS.\n");
        sb.append(
                "queryCNS                                 Query CNS information by contract name and contract version.\n");
        sb.append("addSealer                                Add a sealer node.\n");
        sb.append("addObserver                              Add an observer node.\n");
        sb.append("removeNode                               Remove a node.\n");
        sb.append("setSystemConfigByKey                     Set a system config.\n");
        sb.append("getSystemConfigByKey                     Query a system config value by key.\n");
        sb.append(
                "grantPermissionManager                   Grant permission for permission configuration by address.\n");
        sb.append(
                "revokePermissionManager                  Revoke permission for permission configuration by address.\n");
        sb.append(
                "listPermissionManager                    Query permission information for permission configuration.\n");
        sb.append(
                "grantUserTableManager                    Grant permission for user table by table name and address.\n");
        sb.append(
                "revokeUserTableManager                   Revoke permission for user table by table name and address.\n");
        sb.append(
                "listUserTableManager                     Query permission for user table information.\n");
        sb.append(
                "grantDeployAndCreateManager              Grant permission for deploy contract and create user table by address.\n");
        sb.append(
                "revokeDeployAndCreateManager             Revoke permission for deploy contract and create user table by address.\n");
        sb.append(
                "listDeployAndCreateManager               Query permission information for deploy contract and create user table.\n");
        sb.append(
                "grantNodeManager                         Grant permission for node configuration by address.\n");
        sb.append(
                "revokeNodeManager                        Revoke permission for node configuration by address.\n");
        sb.append(
                "listNodeManager                          Query permission information for node configuration.\n");
        sb.append("grantCNSManager                          Grant permission for CNS by address.\n");
        sb.append("revokeCNSManager                         Revoke permission for CNS by address.\n");
        sb.append("listCNSManager                           Query permission information for CNS.\n");
        sb.append(
                "grantSysConfigManager                    Grant permission for system configuration by address.\n");
        sb.append(
                "revokeSysConfigManager                   Revoke permission for system configuration by address.\n");
        sb.append(
                "listSysConfigManager                     Query permission information for system configuration.\n");
        sb.append("quit(q)                                  Quit console.");
        System.out.println(sb.toString());
        ConsoleUtils.singleLine();
        System.out.println();
    }

    @Override
    public void getNodeVersion(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getNodeVersion")) {
            return;
        }
        String nodeVersion = web3j.getNodeVersion().sendForReturnString();
        ConsoleUtils.printJson(nodeVersion);
        System.out.println();
    }

    @Override
    public void switchGroupID(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("switch");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("switch");
            return;
        }
        String groupIDStr = params[1];
        if ("-h".equals(groupIDStr) || "--help".equals(groupIDStr)) {
            HelpInfo.switchGroupIDHelp();
            return;
        }
        int toGroupID = 1;
        try {
            toGroupID = Integer.parseInt(groupIDStr);
            if(toGroupID <= 0)
            {
              System.out.println("Please provide group ID by positive integer mode(1~2147483647).");
              System.out.println();
              return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Please provide group ID by positive integer mode(1~2147483647).");
            System.out.println();
            return;
        }
        List<String> groupList = web3j.getGroupList().send().getGroupList();
        if (!groupList.contains(toGroupID+"")) {
            System.out.println("Group " + toGroupID + " does not exist. The group list is " + groupList + ".");
            System.out.println();
            return;
        }
				context = new ClassPathXmlApplicationContext("classpath:applicationContext.xml");
        Service service = context.getBean(Service.class);
        GroupChannelConnectionsConfig groupChannelConnectionsConfig = service.getAllChannelConnections();
        List<ChannelConnections> allChannelConnections = groupChannelConnectionsConfig.getAllChannelConnections();
        boolean flag = false;
        for (ChannelConnections channelConnection : allChannelConnections) {
        	if(channelConnection.getGroupId() == toGroupID)
        	{
        		flag = true;
        		break;
        	}
				}
        if(flag)
        {
        	service.setGroupId(toGroupID);
        	try {
						service.run();
					} catch (Exception e) {
	        	System.out.println(
	              "Switch to group "+ toGroupID +" failed! Please check the node status and the console configruation.");
			    	System.out.println();
			    	return;
					}
        }
        else 
        {
        	System.out.println(
              "Switch to group "+ toGroupID +" failed! Please check the node status and the console configruation.");
		    	System.out.println();
		    	return;
        }
        ChannelEthereumService channelEthereumService = new ChannelEthereumService();
        channelEthereumService.setChannelService(service);
        channelEthereumService.setTimeout(60000);
        web3j = Web3j.build(channelEthereumService, groupID);
        groupID = toGroupID;
        System.out.println("Switched to group " + groupID + ".");
        System.out.println();
    }

    @Override
    public void getBlockNumber(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getBlockNumber")) {
            return;
        }
        String blockNumber = web3j.getBlockNumber().sendForReturnString();
        System.out.println(Numeric.decodeQuantity(blockNumber));
        System.out.println();
    }

    @Override
    public void getPbftView(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getPbftView")) {
            return;
        }
        String pbftView = web3j.getPbftView().sendForReturnString();
        System.out.println(Numeric.decodeQuantity(pbftView));
        System.out.println();
    }

    @Override
    public void getObserverList(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getObserverList")) {
            return;
        }
        List<String> observerList = web3j.getObserverList().send().getResult();
        String observers = observerList.toString();
        if ("[]".equals(observers)) {
            System.out.println("[]");
        } else {
            ConsoleUtils.printJson(observers);
        }
        System.out.println();
    }

    @Override
    public void getSealerList(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getSealerList")) {
            return;
        }
        List<String> sealerList = web3j.getSealerList().send().getResult();
        String sealers = sealerList.toString();
        if ("[]".equals(sealers)) {
            System.out.println("[]");
        } else {
            ConsoleUtils.printJson(sealers);
        }
        System.out.println();
    }

    @Override
    public void getConsensusStatus(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getConsensusStatus")) {
            return;
        }
        String consensusStatus = web3j.getConsensusStatus().sendForReturnString();
        ConsoleUtils.printJson(consensusStatus);
        System.out.println();
    }

    @Override
    public void getSyncStatus(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getSyncStatus")) {
            return;
        }
        String syncStatus = web3j.getSyncStatus().sendForReturnString();
        ConsoleUtils.printJson(syncStatus);
        System.out.println();
    }

    @Override
    public void getPeers(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getPeers")) {
            return;
        }
        String peers = web3j.getPeers().sendForReturnString();
        ConsoleUtils.printJson(peers);
        System.out.println();
    }

    @Override
    public void getNodeIDList(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getNodeIDList")) {
            return;
        }
        List<String> nodeIds = web3j.getNodeIDList().send().getResult();
        ConsoleUtils.printJson(nodeIds.toString());
        System.out.println();
    }

    @Override
    public void getGroupPeers(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getGroupPeers")) {
            return;
        }
        List<String> groupPeers = web3j.getGroupPeers().send().getResult();
        ConsoleUtils.printJson(groupPeers.toString());
        System.out.println();
    }

    @Override
    public void getGroupList(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getGroupList")) {
            return;
        }
        List<String> groupList = web3j.getGroupList().send().getResult();
        System.out.println(groupList);
        System.out.println();
    }

    @Override
    public void getBlockByHash(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("getBlockByHash");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("getBlockByHash");
            return;
        }
        String blockHash = params[1];
        if ("-h".equals(blockHash) || "--help".equals(blockHash)) {
            HelpInfo.getBlockByHashHelp();
            return;
        }
        if (ConsoleUtils.isInvalidHash(blockHash)) return;
        boolean flag = false;
        if (params.length == 3) {
            if ("true".equals(params[2])) {
                flag = true;
            } else if ("false".equals(params[2])) {
                flag = false;
            } else {
                System.out.println("Please provide true or false for the second parameter.");
                System.out.println();
                return;
            }
        }
        String block = web3j.getBlockByHash(blockHash, flag).sendForReturnString();
        ConsoleUtils.printJson(block);
        System.out.println();
    }

    @Override
    public void getBlockByNumber(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("getBlockByNumber");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("getBlockByNumber");
            return;
        }
        String blockNumberStr1 = params[1];
        if ("-h".equals(blockNumberStr1) || "--help".equals(blockNumberStr1)) {
            HelpInfo.getBlockByNumberHelp();
            return;
        }
        if (ConsoleUtils.isInvalidNumber(blockNumberStr1, 0)) return;
        BigInteger blockNumber1 = new BigInteger(blockNumberStr1);
        String blockNumberStr2 = web3j.getBlockNumber().sendForReturnString();
        BigInteger blockNumber2 = Numeric.decodeQuantity(blockNumberStr2);
        if (blockNumber1.compareTo(blockNumber2) > 0) {
            System.out.println("BlockNumber does not exist.");
            System.out.println();
            return;
        }
        boolean flag = false;
        if (params.length == 3) {
            if ("true".equals(params[2])) {
                flag = true;
            } else if ("false".equals(params[2])) {
                flag = false;
            } else {
                System.out.println("Please provide true or false for the second parameter.");
                System.out.println();
                return;
            }
        }
        String block =
                web3j
                        .getBlockByNumber(DefaultBlockParameter.valueOf(blockNumber1), flag)
                        .sendForReturnString();
        ConsoleUtils.printJson(block);
        System.out.println();
    }

    @Override
    public void getBlockHashByNumber(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("getBlockHashByNumber");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("getBlockHashByNumber");
            return;
        }
        String blockNumberStr = params[1];
        if ("-h".equals(blockNumberStr) || "--help".equals(blockNumberStr)) {
            HelpInfo.getBlockHashByNumberHelp();
            return;
        }
        if (ConsoleUtils.isInvalidNumber(blockNumberStr, 0)) return;
        BigInteger blockNumber = new BigInteger(blockNumberStr);
        BigInteger getBlockNumber =
                Numeric.decodeQuantity(web3j.getBlockNumber().sendForReturnString());
        if (blockNumber.compareTo(getBlockNumber) > 0) {
            System.out.println("This block number doesn't exsit.");
            System.out.println();
            return;
        }
        String blockHash =
                web3j
                        .getBlockHashByNumber(DefaultBlockParameter.valueOf(blockNumber))
                        .sendForReturnString();
        ConsoleUtils.printJson(blockHash);
        System.out.println();
    }

    @Override
    public void getTransactionByHash(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("getTransactionByHash");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("getTransactionByHash");
            return;
        }
        String transactionHash = params[1];
        if ("-h".equals(transactionHash) || "--help".equals(transactionHash)) {
            HelpInfo.getTransactionByHashHelp();
            return;
        }
        if (ConsoleUtils.isInvalidHash(transactionHash)) return;
        String transaction = web3j.getTransactionByHash(transactionHash).sendForReturnString();
        if ("null".equals(transaction)) {
            System.out.println("This transaction hash doesn't exist.");
            return;
        }
        ConsoleUtils.printJson(transaction);
        System.out.println();
    }

    @Override
    public void getTransactionByBlockHashAndIndex(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("getTransactionByBlockHashAndIndex");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("getTransactionByBlockHashAndIndex");
            return;
        }
        String blockHash = params[1];
        if ("-h".equals(blockHash) || "--help".equals(blockHash)) {
            HelpInfo.getTransactionByBlockHashAndIndexHelp();
            return;
        }
        if (params.length < 3) {
            HelpInfo.promptHelp("getTransactionByBlockHashAndIndex");
            return;
        }
        if (ConsoleUtils.isInvalidHash(blockHash)) return;
        String indexStr = params[2];
        if (ConsoleUtils.isInvalidNumber(indexStr, 1)) return;
        BigInteger index = new BigInteger(indexStr);
        String transaction =
                web3j.getTransactionByBlockHashAndIndex(blockHash, index).sendForReturnString();
        ConsoleUtils.printJson(transaction);
        System.out.println();
    }

    @Override
    public void getTransactionByBlockNumberAndIndex(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("getTransactionByBlockNumberAndIndex");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("getTransactionByBlockNumberAndIndex");
            return;
        }
        String blockNumberStr = params[1];
        if ("-h".equals(blockNumberStr) || "--help".equals(blockNumberStr)) {
            HelpInfo.getTransactionByBlockNumberAndIndexHelp();
            return;
        }
        if (params.length < 3) {
            HelpInfo.promptHelp("getTransactionByBlockNumberAndIndex");
            return;
        }
        if (ConsoleUtils.isInvalidNumber(blockNumberStr, 0)) return;
        BigInteger blockNumber = new BigInteger(blockNumberStr);
        String indexStr = params[2];
        if (ConsoleUtils.isInvalidNumber(indexStr, 1)) return;
        BigInteger index = new BigInteger(indexStr);
        String transaction =
                web3j
                        .getTransactionByBlockNumberAndIndex(DefaultBlockParameter.valueOf(blockNumber), index)
                        .sendForReturnString();
        ConsoleUtils.printJson(transaction);
        System.out.println();
    }

    @Override
    public void getTransactionReceipt(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("getTransactionReceipt");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("getTransactionReceipt");
            return;
        }
        String transactionHash = params[1];
        if ("-h".equals(transactionHash) || "--help".equals(transactionHash)) {
            HelpInfo.getTransactionReceiptHelp();
            return;
        }
        if (ConsoleUtils.isInvalidHash(transactionHash)) return;
        String transactionReceipt = web3j.getTransactionReceipt(transactionHash).sendForReturnString();
        if ("null".equals(transactionReceipt)) {
            System.out.println("This transaction hash doesn't exist.");
            return;
        }
        ConsoleUtils.printJson(transactionReceipt);
        System.out.println();
    }

    @Override
    public void getPendingTxSize(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getPendingTxSize")) {
            return;
        }
        String size = web3j.getPendingTxSize().sendForReturnString();
        System.out.println(Numeric.decodeQuantity(size));
        System.out.println();
    }

    @Override
    public void getPendingTransactions(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getPendingTransactions")) {
            return;
        }
        String pendingTransactions = web3j.getPendingTransaction().sendForReturnString();
        if ("[]".equals(pendingTransactions)) System.out.println(pendingTransactions);
        else ConsoleUtils.printJson(pendingTransactions);
        System.out.println();
    }

    @Override
    public void getCode(String[] params) throws IOException {
        if (params.length < 2) {
            HelpInfo.promptHelp("getCode");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("getCode");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.getCodeHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        String code = web3j.getCode(address, DefaultBlockParameterName.LATEST).sendForReturnString();
        if ("0x".equals(code)) {
            System.out.println("This address doesn't exist.");
            System.out.println();
            return;
        }
        ConsoleUtils.printJson(code);
        System.out.println();
    }

    @Override
    public void getTotalTransactionCount(String[] params) throws IOException {
        if (HelpInfo.promptNoParams(params, "getTotalTransactionCount")) {
            return;
        }
        String transactionCount = web3j.getTotalTransactionCount().sendForReturnString();
        JSONObject jo = JSONObject.parseObject(transactionCount);
        jo.put("txSum", Numeric.decodeQuantity(jo.get("txSum").toString()));
        jo.put("blockNumber", Numeric.decodeQuantity(jo.get("blockNumber").toString()));
        ConsoleUtils.printJson(jo.toJSONString());
        System.out.println();
    }

    @Override
    public void deploy(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("deploy");
            return;
        }
        if ("-h".equals(params[1]) || "--help".equals(params[1])) {
            HelpInfo.deployHelp();
            return;
        }
        String name = params[1];
        try {
            ConsoleUtils.dynamicCompileSolFilesToJava();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println();
            return;
        }
        if (name.endsWith(".sol")) {
            name = name.substring(0, name.length() - 4);
        }
        ConsoleUtils.dynamicCompileJavaToClass(name);
        ConsoleUtils.dynamicLoadClass();
        contractName = ConsoleUtils.PACKAGENAME + "." + name;
        try {
            contractClass = getContractClass(contractName);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(
                    "There is no " + name + ".sol" + " in the directory of solidity/contracts.");
            System.out.println();
            return;
        }
        Method method = ContractClassFactory.getDeployFunction(contractClass);

        Type[] classType = method.getParameterTypes();
        String[] generic = new String[method.getParameterCount()];
        for (int i = 0; i < classType.length; i++) {
            generic[i] = method.getGenericParameterTypes()[i].getTypeName();
        }
        Class[] classList = new Class[classType.length];
        for (int i = 0; i < classType.length; i++) {
            Class clazz = (Class) classType[i];
            classList[i] = clazz;
        }

        String[] newParams = new String[params.length - 2];
        System.arraycopy(params, 2, newParams, 0, params.length - 2);
        Object[] obj = getDeployPrametersObject("deploy", classList, newParams, generic);
        remoteCall = (RemoteCall<?>) method.invoke(null, obj);
        try {
        	Contract contract = (Contract) remoteCall.send();
      	  contractAddress = contract.getContractAddress();
          System.out.println(contractAddress);
          System.out.println();
        } catch (Exception e) {
            if (e.getMessage().contains("0x19")) {
                ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.PermissionDenied));
            } else {
                throw e;
            }
        }
       
    }

    @Override
    public void call(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("call");
            return;
        }
        if ("-h".equals(params[1]) || "--help".equals(params[1])) {
            HelpInfo.callHelp();
            return;
        }
        if (params.length < 4) {
            HelpInfo.promptHelp("call");
            return;
        }
        String name = params[1];
        if (name.endsWith(".sol")) {
            name = name.substring(0, name.length() - 4);
        }
        contractName = ConsoleUtils.PACKAGENAME + "." + name;
        ConsoleUtils.dynamicLoadClass();
        try {
            contractClass = getContractClass(contractName);
        } catch (Exception e) {
            System.out.println(
                    "There is no "
                            + name
                            + ".class"
                            + " in the directory of java/classes/org/fisco/bcos/temp");
            System.out.println();
            return;
        }
        Method load =
                contractClass.getMethod(
                        "load",
                        String.class,
                        Web3j.class,
                        Credentials.class,
                        BigInteger.class,
                        BigInteger.class);
        Object contractObject;

        contractAddress = params[2];
        if (ConsoleUtils.isInvalidAddress(contractAddress)) {
            return;
        }
        contractObject = load.invoke(null, contractAddress, web3j, credentials, gasPrice, gasLimit);
        String funcName = params[3];
        Method[] methods = contractClass.getDeclaredMethods();
        Method method = ContractClassFactory.getMethodByName(funcName, methods);
        String[] generic = new String[method.getParameterCount()];
        Type[] classType = method.getParameterTypes();
        for (int i = 0; i < classType.length; i++) {
            generic[i] = method.getGenericParameterTypes()[i].getTypeName();
        }
        Class[] classList = new Class[classType.length];
        for (int i = 0; i < classType.length; i++) {
            Class clazz = (Class) classType[i];
            classList[i] = clazz;
        }

        Class[] parameterType =
                ContractClassFactory.getParameterType(contractClass, funcName, params.length - 4);
        if (parameterType == null) {
            HelpInfo.promptNoFunc(params[1], funcName, params.length - 4);
            return;
        }
        Method func = contractClass.getMethod(funcName, parameterType);
        String[] newParams = new String[params.length - 4];
        System.arraycopy(params, 4, newParams, 0, params.length - 4);
        Object[] argobj = ContractClassFactory.getPrametersObject(funcName, parameterType, newParams, generic);
        if (argobj == null) {
            return;
        }

        remoteCall = (RemoteCall<?>) func.invoke(contractObject, argobj);
        Object result = remoteCall.send();
        String returnObject =
                ContractClassFactory.getReturnObject(contractClass, funcName, parameterType, result);
        if (returnObject == null) {
            HelpInfo.promptNoFunc(params[1], funcName, params.length - 4);
            return;
        }
        System.out.println(returnObject);
        System.out.println();

    }

    @Override
    public void deployByCNS(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("deployByCNS");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("deployByCNS");
            return;
        }
        if ("-h".equals(params[1]) || "--help".equals(params[1])) {
            HelpInfo.deployByCNSHelp();
            return;
        }
        if (params.length < 3) {
            HelpInfo.promptHelp("deployByCNS");
            return;
        }
        PermissionService permissionTableService = new PermissionService(web3j, credentials);
        List<PermissionInfo> permissions = permissionTableService.listCNSManager();
        boolean flag = false;
        if (permissions.size() == 0) {
            flag = true;
        } else {
            for (PermissionInfo permission : permissions) {
                if ((credentials.getAddress()).equals(permission.getAddress())) {
                    flag = true;
                    break;
                }
            }
        }
        if (!flag) {
            ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.PermissionDenied));
            System.out.println();
            return;
        }

        String name = params[1];
        if (name.endsWith(".sol")) {
            name = name.substring(0, name.length() - 4);
        }
        CnsService cnsService = new CnsService(web3j, credentials);
        List<CnsInfo> qcns = cnsService.queryCnsByNameAndVersion(name, params[2]);
        if (qcns.size() != 0) {
            ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.ContractNameAndVersionExist));
            System.out.println();
            return;
        }
        try {
            ConsoleUtils.dynamicCompileSolFilesToJava();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println();
            return;
        }
        contractName = ConsoleUtils.PACKAGENAME + "." + name;
        ConsoleUtils.dynamicCompileJavaToClass(name);
        ConsoleUtils.dynamicLoadClass();
        try {
            contractClass = getContractClass(contractName);
        } catch (Exception e) {
            System.out.println(
                    "There is no " + name + ".sol" + " in the directory of solidity/contracts.");
            System.out.println();
            return;
        }
        ContractGasProvider gasProvider = new StaticGasProvider(gasPrice, gasLimit);
        Method deploy =
                contractClass.getMethod(
                        "deploy", Web3j.class, Credentials.class, ContractGasProvider.class);
        remoteCall = (RemoteCall<?>) deploy.invoke(null, web3j, credentials, gasProvider);
        contractVersion = params[2];
        if (contractVersion.length() > CnsService.MAX_VERSION_LENGTH) {
            ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.VersionExceeds));
            System.out.println();
            return;
        }
        try {
        		Contract contract = (Contract) remoteCall.send();
            contractAddress = contract.getContractAddress();
            // register cns
            String result = cnsService.registerCns(name, contractVersion, contractAddress, "");
            System.out.println(contractAddress);
            System.out.println();
        } catch (Exception e) {
            if (e.getMessage().contains("0x19")) {
                ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.PermissionDenied));
            } else {
                throw e;
            }
        }

    }

    @SuppressWarnings("rawtypes")
    @Override
    public void callByCNS(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("callByCNS");
            return;
        }
        if ("-h".equals(params[1]) || "--help".equals(params[1])) {
            HelpInfo.callByCNSHelp();
            return;
        }
        if (params.length < 4) {
            HelpInfo.promptHelp("callByCNS");
            return;
        }
        ConsoleUtils.dynamicLoadClass();
        String name = params[1];
        if (name.endsWith(".sol")) {
            name = name.substring(0, name.length() - 4);
        }
        contractName = ConsoleUtils.PACKAGENAME + "." + name;
        try {
            contractClass = getContractClass(contractName);
        } catch (Exception e) {
            System.out.println(
                    "There is no "
                            + name
                            + ".class"
                            + " in the directory of java/classes/org/fisco/bcos/temp");
            System.out.println();
            return;
        }
        Method load =
                contractClass.getMethod(
                        "load",
                        String.class,
                        Web3j.class,
                        Credentials.class,
                        BigInteger.class,
                        BigInteger.class);
        Object contractObject;

        // get address from cns
        contractName = name;
        contractVersion = params[2];
        CnsService cnsResolver = new CnsService(web3j, credentials);
        try {
            contractAddress =
                    cnsResolver.getAddressByContractNameAndVersion(contractName + ":" + contractVersion);
        } catch (Exception e) {
            System.out.println(
                    "The contract " + contractName + " for version " + contractVersion + " doesn't exsit.");
            System.out.println();
            return;
        }
        contractObject = load.invoke(null, contractAddress, web3j, credentials, gasPrice, gasLimit);
        String funcName = params[3];
        Method[] methods = contractClass.getMethods();
        Class[] type = null;
        Method method = ContractClassFactory.getMethodByName(funcName, methods);
        String[] generic = new String[method.getParameterCount()];
        Type[] classType = method.getParameterTypes();
        for (int i = 0; i < classType.length; i++) {
            generic[i] = method.getGenericParameterTypes()[i].getTypeName();
        }

        Class[] classList = new Class[classType.length];
        for (int i = 0; i < classType.length; i++) {
            Class clazz = (Class) classType[i];
            classList[i] = clazz;
        }
        Class[] parameterType =
                ContractClassFactory.getParameterType(contractClass, funcName, params.length - 4);
        if (parameterType == null) {
            HelpInfo.promptNoFunc(params[1], funcName, params.length - 4);
            return;
        }
        Method func = contractClass.getMethod(funcName, parameterType);
        String[] newParams = new String[params.length - 4];
        System.arraycopy(params, 4, newParams, 0, params.length - 4);
        Object[] argobj = ContractClassFactory.getPrametersObject(funcName, parameterType, newParams, generic);
        if (argobj == null) {
            return;
        }
        remoteCall = (RemoteCall<?>) func.invoke(contractObject, argobj);
        Object result = remoteCall.send();
        String returnObject =
                ContractClassFactory.getReturnObject(contractClass, funcName, parameterType, result);
        if (returnObject == null) {
            HelpInfo.promptNoFunc(params[1], funcName, params.length - 4);
            return;
        }
        System.out.println(returnObject);
        System.out.println();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void queryCNS(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("queryCNS");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("queryCNS");
            return;
        }
        if ("-h".equals(params[1]) || "--help".equals(params[1])) {
            HelpInfo.queryCNSHelp();
            return;
        }

        CnsService cnsService = new CnsService(web3j, credentials);
        List<CnsInfo> cnsInfos = new ArrayList<>();
        contractName = params[1];
        if (contractName.endsWith(".sol")) {
            contractName = contractName.substring(0, contractName.length() - 4);
        }
        if (params.length == 3) {
            contractVersion = params[2];
            cnsInfos = cnsService.queryCnsByNameAndVersion(contractName, contractVersion);
        } else {
            cnsInfos = cnsService.queryCnsByName(contractName);
        }

        if (cnsInfos.isEmpty()) {
            System.out.println("Empty set.");
            System.out.println();
            return;
        }
        ConsoleUtils.singleLineForTable();
        String[] headers = {"version", "address"};
        int size = cnsInfos.size();
        String[][] data = new String[size][2];
        for (int i = 0; i < size; i++) {
            data[i][0] = cnsInfos.get(i).getVersion();
            data[i][1] = cnsInfos.get(i).getAddress();
        }
        ColumnFormatter<String> cf = ColumnFormatter.text(Alignment.CENTER, 45);
        Table table = Table.of(headers, data, cf);
        System.out.println(table);
        ConsoleUtils.singleLineForTable();
        System.out.println();
    }

    @Override
    public void addSealer(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("addSealer");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("addSealer");
            return;
        }
        String nodeId = params[1];
        if ("-h".equals(nodeId) || "--help".equals(nodeId)) {
            HelpInfo.addSealerHelp();
            return;
        }
        if (nodeId.length() != 128) {
            ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.InvalidNodeId));
        } else {
            ConsensusService consensusService = new ConsensusService(web3j, credentials);
            String result;
            result = consensusService.addSealer(nodeId);
            ConsoleUtils.printJson(result);
        }
        System.out.println();
    }

    @Override
    public void addObserver(String[] params) throws Exception {

        if (params.length < 2) {
            HelpInfo.promptHelp("addObserver");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("addObserver");
            return;
        }
        String nodeId = params[1];
        if ("-h".equals(nodeId) || "--help".equals(nodeId)) {
            HelpInfo.addObserverHelp();
            return;
        }
        if (nodeId.length() != 128) {
            ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.InvalidNodeId));
        } else {
						ConsensusService consensusService = new ConsensusService(web3j, credentials);
						String result = consensusService.addObserver(nodeId);
						ConsoleUtils.printJson(result);
        }
        System.out.println();
    }

    @Override
    public void removeNode(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("removeNode");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("removeNode");
            return;
        }
        String nodeId = params[1];
        if ("-h".equals(nodeId) || "--help".equals(nodeId)) {
            HelpInfo.removeNodeHelp();
            return;
        }
        if (nodeId.length() != 128) {
            ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.InvalidNodeId));
        } else {
            ConsensusService consensusService = new ConsensusService(web3j, credentials);
            String result = null;
            result = consensusService.removeNode(nodeId);
            ConsoleUtils.printJson(result);
        }
        System.out.println();
    }

    @Override
    public void grantUserTableManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("grantUserTableManager");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("grantUserTableManager");
            return;
        }
        String tableName = params[1];
        if ("-h".equals(tableName) || "--help".equals(tableName)) {
            HelpInfo.grantUserTableManagerHelp();
            return;
        }
        if (params.length < 3) {
            HelpInfo.promptHelp("grantUserTableManager");
            return;
        }
        String addr = params[2];
        if (ConsoleUtils.isInvalidAddress(addr)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result = null;
        result = permission.grantUserTableManager(tableName, addr);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void revokeUserTableManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("revokeUserTableManager");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("revokeUserTableManager");
            return;
        }
        String tableName = params[1];
        if ("-h".equals(tableName) || "--help".equals(tableName)) {
            HelpInfo.revokeUserTableManagerHelp();
            return;
        }
        if (params.length < 3) {
            HelpInfo.promptHelp("revokeUserTableManager");
            return;
        }
        String addr = params[2];
        if (ConsoleUtils.isInvalidAddress(addr)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result = null;
        result = permission.revokeUserTableManager(tableName, addr);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void listUserTableManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("listUserTableManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("listUserTableManager");
            return;
        }
        String tableName = params[1];
        if ("-h".equals(tableName) || "--help".equals(tableName)) {
            HelpInfo.listUserTableManagerHelp();
            return;
        }
        PermissionService permissionTableService = new PermissionService(web3j, credentials);
        List<PermissionInfo> permissions = permissionTableService.listUserTableManager(tableName);
        printPermissionInfo(permissions);
    }

    @Override
    public void grantDeployAndCreateManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("grantDeployAndCreateManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("grantDeployAndCreateManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.grantDeployAndCreateManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.grantDeployAndCreateManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void revokeDeployAndCreateManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("revokeDeployAndCreateManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("revokeDeployAndCreateManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.revokeDeployAndCreateManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.revokeDeployAndCreateManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void listDeployAndCreateManager(String[] params) throws Exception {
        if (HelpInfo.promptNoParams(params, "listyDeployAndCreateManager")) {
            return;
        }
        PermissionService permissionTableService = new PermissionService(web3j, credentials);
        List<PermissionInfo> permissions = permissionTableService.listDeployAndCreateManager();
        printPermissionInfo(permissions);
    }

    @Override
    public void grantPermissionManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("grantPermissionManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("grantPermissionManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.grantPermissionManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.grantPermissionManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void revokePermissionManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("revokePermissionManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("revokePermissionManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.revokePermissionManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.revokePermissionManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void listPermissionManager(String[] params) throws Exception {
        if (HelpInfo.promptNoParams(params, "listPermissionManager")) {
            return;
        }
        PermissionService permissionTableService = new PermissionService(web3j, credentials);
        List<PermissionInfo> permissions = permissionTableService.listPermissionManager();
        printPermissionInfo(permissions);
    }

    @Override
    public void grantNodeManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("grantNodeManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("grantNodeManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.grantNodeManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.grantNodeManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void revokeNodeManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("revokeNodeManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("revokeNodeManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.revokeNodeManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.revokeNodeManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void listNodeManager(String[] params) throws Exception {
        if (HelpInfo.promptNoParams(params, "listNodeManager")) {
            return;
        }
        PermissionService permissionTableService = new PermissionService(web3j, credentials);
        List<PermissionInfo> permissions = permissionTableService.listNodeManager();
        printPermissionInfo(permissions);
    }

    @Override
    public void grantCNSManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("grantCNSManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("grantCNSManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.grantCNSManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.grantCNSManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void revokeCNSManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("revokeCNSManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("revokeCNSManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.revokeCNSManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.revokeCNSManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void listCNSManager(String[] params) throws Exception {
        if (HelpInfo.promptNoParams(params, "listCNSManager")) {
            return;
        }
        PermissionService permissionTableService = new PermissionService(web3j, credentials);
        List<PermissionInfo> permissions = permissionTableService.listCNSManager();
        printPermissionInfo(permissions);
    }

    @Override
    public void grantSysConfigManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("grantSysConfigManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("grantSysConfigManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.grantSysConfigManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.grantSysConfigManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void revokeSysConfigManager(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("revokeSysConfigManager");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("revokeSysConfigManager");
            return;
        }
        String address = params[1];
        if ("-h".equals(address) || "--help".equals(address)) {
            HelpInfo.revokeSysConfigManagerHelp();
            return;
        }
        if (ConsoleUtils.isInvalidAddress(address)) {
            return;
        }
        PermissionService permission = new PermissionService(web3j, credentials);
        String result;
        result = permission.revokeSysConfigManager(address);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void listSysConfigManager(String[] params) throws Exception {
        if (HelpInfo.promptNoParams(params, "listSysConfigManager")) {
            return;
        }
        PermissionService permissionTableService = new PermissionService(web3j, credentials);
        List<PermissionInfo> permissions = permissionTableService.listSysConfigManager();
        printPermissionInfo(permissions);
    }

    @Override
    public void setSystemConfigByKey(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("setSystemConfigByKey");
            return;
        }
        if (params.length > 3) {
            HelpInfo.promptHelp("setSystemConfigByKey");
            return;
        }
        String key = params[1];
        if ("-h".equals(key) || "--help".equals(key)) {
            HelpInfo.setSystemConfigByKeyHelp();
            return;
        }
        if (params.length < 3) {
            HelpInfo.promptHelp("setSystemConfigByKey");
            return;
        }
        String value = params[2];

        String[] args = {"setSystemConfig", key, value};
        SystemConfigSerivce systemConfigSerivce = new SystemConfigSerivce(web3j, credentials);
        String result;
        result = systemConfigSerivce.setValueByKey(key, value);
        ConsoleUtils.printJson(result);
        System.out.println();
    }

    @Override
    public void getSystemConfigByKey(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("getSystemConfigByKey");
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("getSystemConfigByKey");
            return;
        }
        String key = params[1];
        if ("-h".equals(key) || "--help".equals(key)) {
            HelpInfo.getSystemConfigByKeyHelp();
            return;
        }
        String[] args = {"getSystemConfigByKey", key};
        String value = web3j.getSystemConfigByKey(key).sendForReturnString();
        System.out.println(value);
        System.out.println();
    }

    private void printPermissionInfo(List<PermissionInfo> permissionInfos) {
        if (permissionInfos.isEmpty()) {
            System.out.println("Empty set.");
            System.out.println();
            return;
        }
        ConsoleUtils.singleLineForTable();
        String[] headers = {"address", "enable_num"};
        int size = permissionInfos.size();
        String[][] data = new String[size][2];
        for (int i = 0; i < size; i++) {
            data[i][0] = permissionInfos.get(i).getAddress();
            data[i][1] = permissionInfos.get(i).getEnableNum();
        }
        ColumnFormatter<String> cf = ColumnFormatter.text(Alignment.CENTER, 45);
        Table table = Table.of(headers, data, cf);
        System.out.println(table);
        ConsoleUtils.singleLineForTable();
        System.out.println();
    }

    public static Object[] getDeployPrametersObject(String funcName, Class[] type, String[] params, String[] generic) {
        Object[] obj = new Object[params.length + 3];
        obj[0] = web3j;
        obj[1] = credentials;
        obj[2] = new StaticGasProvider(gasPrice, gasLimit);

        for (int i = 0; i < params.length; i++) {
            if (type[i + 3] == String.class) {
                if (params[i].startsWith("\"") && params[i].endsWith("\"")) {
                    try {
                        obj[i + 3] = params[i].substring(1, params[i].length() - 1);
                    } catch (Exception e) {
                        System.out.println(
                                "Please provide double quote for String that cannot contain any blank spaces.");
                        System.out.println();
                        return null;
                    }
                }
            } else if (type[i + 3] == Boolean.class) {
                try {
                    obj[i + 3] = Boolean.parseBoolean(params[i]);
                } catch (Exception e) {
                    System.out.println(
                            "The " + (i + 1) + "th parameter of " + funcName + " needs boolean value.");
                    System.out.println();
                    return null;
                }
            } else if (type[i + 3] == BigInteger.class) {
                try {
                    obj[i + 3] = new BigInteger(params[i]);
                } catch (Exception e) {
                    System.out.println(
                            "The " + (i + 1) + "th parameter of " + funcName + " needs integer value.");
                    System.out.println();
                    return null;
                }
            } else if (type[i + 3] == byte[].class) {
                if (params[i].startsWith("\"") && params[i].endsWith("\"")) {
                    byte[] bytes2 = params[i + 3].substring(1, params[i + 3].length() - 1).getBytes();
                    byte[] bytes1 = new byte[bytes2.length];
                    for (int j = 0; j < bytes2.length; j++) {
                        bytes1[j] = bytes2[j];
                    }
                    obj[i + 3] = bytes1;
                } else {
                    System.out.println("Please provide double quote for byte String.");
                    System.out.println();
                    return null;
                }
            } else if (type[i + 3] == List.class) {

                if (params[i].startsWith("[") && params[i].endsWith("]")) {
                    try {
                        String listParams = params[i].substring(1, params[i].length() - 1);
                        String[] ilist = listParams.split(",");
                        List paramsList = new ArrayList();
                        if (generic[i].contains("String")) {
                            paramsList = new ArrayList<String>();
                            for (int j = 0; j < ilist.length; j++) {
                                paramsList.add(ilist[j].substring(1, ilist[j].length() - 1));
                            }

                        } else if (generic[i].contains("BigInteger")) {
                            paramsList = new ArrayList<BigInteger>();
                            for (int j = 0; j < ilist.length; j++) {
                                paramsList.add(new BigInteger(ilist[j]));
                            }

                        }
                        else if(generic[i].contains("byte[]")) {
                            paramsList = new ArrayList<byte[]>();
                            for (int j = 0; j < ilist.length; j++) {
                                if (ilist[j].startsWith("\"") && ilist[j].endsWith("\"")) {
                                    byte[] bytes = ilist[j].substring(1, ilist[j].length() - 1).getBytes();
                                    byte[] bytes1 = new byte[32];
                                    byte[] bytes2 = bytes;
                                    for (int k = 0; k < bytes2.length; k++) {
                                        bytes1[k] = bytes2[k];
                                    }
                                    paramsList.add(bytes1);
                                }
                            }
                        }
                        obj[i + 3] = paramsList;
                    } catch (Exception e) {
                        System.out.println(
                                "Please provide double quote for String that cannot contain any blank spaces.");
                        System.out.println();
                        return null;
                    }
                }
            }
        }
        return obj;
    }
}
