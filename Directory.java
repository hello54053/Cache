package Demo1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.*;
import java.util.List;

/*
 * 目录协议(Directory)仿真系统
 * 模拟多个CPU节点的私有存储器和缓存状态变化
 */
public class Directory extends JFrame {
    // 系统配置
    private static final int NUM_CPU_NODES = 4;    // CPU节点数量
    private static final int CACHE_BLOCKS = 16;    // 每个CPU的缓存块数量
    private static final int BLOCK_SIZE = 16;      // 每个缓存块的数据大小(字节)
    private static final int PRIVATE_MEM_SIZE = 4 * 1024 * 1024; // 每个节点的私有存储器大小(4MB)

    // 状态枚举
    enum CacheState { 
        INVALID("I"), SHARED("S"), MODIFIED("M");
        private final String abbr;
        CacheState(String abbr) { this.abbr = abbr; }
        public String getAbbr() { return abbr; }
    }
    
    // 目录项状态
    enum DirectoryState {
        UNCACHED("U"), SHARED("S"), EXCLUSIVE("E");
        private final String abbr;
        DirectoryState(String abbr) { this.abbr = abbr; }
        public String getAbbr() { return abbr; }
    }
    
    // CPU节点数据结构
    static class CPUNode {
        String id;                  // CPU节点标识符
        CacheBlock[] cacheBlocks;    // 缓存块数组
        Map<String,String[]> privateMemory;      // 私有存储器(4MB)
        Map<String, DirectoryEntry> directory; // 目录(跟踪其他节点缓存状态)

        public CPUNode(String id) {
            this.id = id;
            this.cacheBlocks = new CacheBlock[CACHE_BLOCKS];
            Arrays.setAll(cacheBlocks, i -> new CacheBlock());
            
             // 初始化内存 (地址 -> 数据)
            this.privateMemory = new HashMap<>();
            initMemory();
            // 初始化目录
            this.directory = new HashMap<>();
        }
        
        private void initMemory() {
        	 for (int i = 0; i < 0x100000; i++) { 
                 String address = String.format("0x%06X", i);
                 privateMemory.put(address, new String[BLOCK_SIZE]);
                 Arrays.fill(privateMemory.get(address), "0"); // 初始数据全为0
             }
        }
        
    }
    
    // 目录项数据结构
    static class DirectoryEntry {
        DirectoryState state;         // 目录状态
        Set<String> sharingSet;      // 共享该块的节点集合
        
        public DirectoryEntry() {
            this.state = DirectoryState.UNCACHED;
            this.sharingSet = new HashSet<>();
        }
    }

    // Cache块数据结构
    static class CacheBlock {
        String index;                // 缓存索引
        String offset;              // 块内偏移
        String tag;                 // 缓存块标记
        CacheState state;           // 缓存块状态(INVALID/SHARED/MODIFIED)
        String[] data;              // 缓存块数据(16字节)
        boolean isDirty = false;    // 脏位标记

        public CacheBlock() {
            this.tag = "-";
            this.state = CacheState.INVALID;
            this.data = new String[BLOCK_SIZE];
            Arrays.fill(data, "0");
        }
    }

    // 主界面组件
    private JPanel cpuNodesPanel;        // CPU节点显示面板
    private JTextArea detailArea;        // 请求详情显示区域
    private JList<String> historyList;   // 请求历史列表
    private DefaultListModel<String> historyModel;  // 历史列表数据模型
    private JComboBox<String> cpuCombo;  // CPU节点选择下拉框
    private JTabbedPane directoryTabbedPane; // 目录状态标签页

    // 系统状态
    private CPUNode[] cpuNodes;          // CPU节点数组
    private List<Map<String, Object>> requestHistory = new ArrayList<>(); // 请求历史记录

    /*
     * 构造函数，初始化仿真系统
     */
    public Directory() {
        initUI();        // 初始化用户界面
        initSystem();    // 初始化系统状态
        renderUI();      // 渲染用户界面
    }

    private void initUI() {
        setTitle("Directory仿真系统");
        setSize(2000, 1200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));
    }

    private void initSystem() {
        cpuNodes = new CPUNode[NUM_CPU_NODES];
        cpuNodes[0] = new CPUNode("CPU00");
        cpuNodes[1] = new CPUNode("CPU01");
        cpuNodes[2] = new CPUNode("CPU10");
        cpuNodes[3] = new CPUNode("CPU11");
        
        // 初始化所有节点的内存为全0
        for (CPUNode node : cpuNodes) {
            node.initMemory();
        }
    }

    private void renderUI() {
        // 顶部控制面板
        JPanel controlPanel = createControlPanel();
        
        // 中部CPU节点和目录显示区
        JPanel centerPanel = new JPanel(new BorderLayout());
        cpuNodesPanel = createCPUNodesPanel();
        directoryTabbedPane = createDirectoryTabs();
        
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setLeftComponent(cpuNodesPanel);
        centerSplit.setRightComponent(directoryTabbedPane);
        centerSplit.setDividerLocation(0.75);
        
        centerPanel.add(centerSplit, BorderLayout.CENTER);

        // 底部历史记录和详情面板
        JSplitPane bottomPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.addListSelectionListener(e -> showRequestDetails());
        
        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setBackground(UIManager.getColor("Panel.background"));
        
        bottomPane.setTopComponent(new JScrollPane(historyList));
        bottomPane.setBottomComponent(new JScrollPane(detailArea));
        bottomPane.setDividerLocation(0.3);

        // 整体布局
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPane, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    /*
     * 创建目录状态标签页
     */
    private JTabbedPane createDirectoryTabs() {
        JTabbedPane tabbedPane = new JTabbedPane();
        
        for (CPUNode node : cpuNodes) {
            JPanel dirPanel = new JPanel(new BorderLayout());
            dirPanel.setBorder(new TitledBorder(node.id + " 目录"));
            
            // 创建目录表模型
            DefaultTableModel model = new DefaultTableModel(new Object[]{"内存地址", "状态", "共享节点"}, 0);
            JTable table = new JTable(model);
            table.setRowHeight(25);
            
            dirPanel.add(new JScrollPane(table), BorderLayout.CENTER);
            tabbedPane.addTab(node.id, dirPanel);
        }
        
        return tabbedPane;
    }
    
    /*
     * 更新目录显示
     */
    private void updateDirectoryTabs() {
        for (int i = 0; i < cpuNodes.length; i++) {
            CPUNode node = cpuNodes[i];
            JPanel dirPanel = (JPanel) directoryTabbedPane.getComponentAt(i);
            JTable table = (JTable) ((JScrollPane) dirPanel.getComponent(0)).getViewport().getView();
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            
            model.setRowCount(0); // 清空现有数据
            
            // 添加目录条目
            for (Map.Entry<String, DirectoryEntry> entry : node.directory.entrySet()) {
                String address = entry.getKey();
                DirectoryEntry dirEntry = entry.getValue();
                String sharingNodes = String.join(", ", dirEntry.sharingSet);
                
                model.addRow(new Object[]{
                    address,
                    dirEntry.state.getAbbr(),
                    sharingNodes.isEmpty() ? "-" : sharingNodes
                });
            }
        }
    }

    /*
     * 创建控制面板，包含地址输入、操作类型选择等组件
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new TitledBorder("内存请求"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 地址输入
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("内存地址 (24位十六进制):"), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        JTextField addressField = new JTextField("0x000000", 12);
        panel.add(addressField, gbc);

        // 操作类型
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("操作类型:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        JComboBox<String> operationCombo = new JComboBox<>(new String[]{"读", "写"});
        panel.add(operationCombo, gbc);

        // 写入值
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("写入值 (16字节十六进制):"), gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        JTextField writeValueField = new JTextField("0000000000000000", 20);
        panel.add(writeValueField, gbc);

        // CPU节点选择
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("目标CPU节点:"), gbc);

        gbc.gridx = 1; gbc.gridy = 3;
        String[] cpuIds = Arrays.stream(cpuNodes).map(n -> n.id).toArray(String[]::new);
        cpuCombo = new JComboBox<>(cpuIds);
        panel.add(cpuCombo, gbc);

        // 按钮
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton executeBtn = new JButton("执行请求");
        JButton resetBtn = new JButton("重置系统");
        
        executeBtn.addActionListener(e -> processRequest(addressField, operationCombo, writeValueField));
        resetBtn.addActionListener(e -> resetSystem());
        
        buttonPanel.add(executeBtn);
        buttonPanel.add(resetBtn);
        panel.add(buttonPanel, gbc);

        return panel;
    }

    /*
     * 创建CPU节点显示面板
     */
    private JPanel createCPUNodesPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 15, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new TitledBorder("CPU Cache状态"));
        
        // 为每个CPU节点创建一个面板
        for (CPUNode node : cpuNodes) {
            JPanel cpuPanel = new JPanel(new BorderLayout());
            cpuPanel.setBorder(new TitledBorder(node.id));
            
            JTable table = new JTable(createCacheTableModel(node));            
            table.setRowHeight(25);
            table.getColumnModel().getColumn(0).setPreferredWidth(50);
            table.getColumnModel().getColumn(1).setPreferredWidth(60);
            table.getColumnModel().getColumn(2).setPreferredWidth(40);
            table.getColumnModel().getColumn(3).setPreferredWidth(200);
            
            cpuPanel.add(new JScrollPane(table), BorderLayout.CENTER);
            panel.add(cpuPanel);
        }
        
        return panel;
    }

    /*
     * 创建缓存表模型，用于在表格中显示缓存状态
     */
    private DefaultTableModel createCacheTableModel(CPUNode node) {
        String[] columns = {"索引", "标记", "状态", "数据 (16字节)"};
        Object[][] data = new Object[CACHE_BLOCKS][4];
        
        for (int i = 0; i < CACHE_BLOCKS; i++) {
            CacheBlock block = node.cacheBlocks[i];
            data[i][0] = i;
            data[i][1] = block.tag;
            data[i][2] = block.state.getAbbr(); // 显示状态缩写
            data[i][3] = String.join("", block.data);
        }
        
        return new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
    
    /*
     * 处理内存请求
     */
    private void processRequest(JTextField addressField, JComboBox<String> operationCombo, JTextField writeValueField) {
        String address = addressField.getText().toUpperCase();
        String operation = (String) operationCombo.getSelectedItem();
        String writeValue = writeValueField.getText().toUpperCase();
        String targetCpuId = (String) cpuCombo.getSelectedItem();
        CPUNode targetNode = getTargetNode(targetCpuId);

        if (!validateInput(address, operation, writeValue)) return;

        // 解析地址，确定归属节点
        String ownerNodeId = getMemoryOwner(address);
        CPUNode ownerNode = getTargetNode(ownerNodeId);
        
        // 解析地址，提取标签和索引
        String tag = address.substring(2, 6);  
        String index = address.substring(6, 7);  
        int cacheIndex = Integer.parseInt(index, 16); 

        // 实际判断缓存命中/缺失
        boolean cacheHit = checkCacheHit(targetNode, tag, cacheIndex);
        
        // 记录请求历史
        recordRequest(address, operation, targetCpuId, cacheHit);
        
        // 处理请求
        if (operation.equals("读")) {
            handleReadRequest(targetNode, ownerNode, address, cacheIndex, cacheHit);
        } else {
            handleWriteRequest(targetNode, ownerNode, address, cacheIndex, writeValue, cacheHit);
        }
        
        // 更新界面
        renderCPUNodes();
        updateDirectoryTabs();
    }

    /*
     * 获取内存地址所属的节点
     * 采用高位交叉编址方式，地址高2位决定节点
     */
    private String getMemoryOwner(String address) {
        int highBits = Integer.parseInt(address.substring(2, 3), 16);
        String binaryStr = String.format("%4s", Integer.toBinaryString(highBits)).replace(' ', '0');
        
        // 提取高2位并转换为节点索引
        String highBitsStr = binaryStr.substring(0, 2);  // 取前2位
        switch(highBitsStr) {
        case "00": return "CPU00";
        case "01": return "CPU01";
        case "10": return "CPU10";
        case "11": return "CPU11";
        default: return "CPU00";
    }
    }
    
    // 检查缓存是否命中
    private boolean checkCacheHit(CPUNode node, String tag, int cacheIndex) {
        CacheBlock block = node.cacheBlocks[cacheIndex];
        return block.tag.equals(tag) && block.state != CacheState.INVALID;
    }
    
    private void handleCacheReplacement(CPUNode node, int cacheIndex) {
        CacheBlock blockToReplace = node.cacheBlocks[cacheIndex];
        
        // 如果要替换的块是有效的（非INVALID状态）
        if (blockToReplace.state != CacheState.INVALID && !blockToReplace.tag.equals("-")) {
            // 重建原始内存地址：tag + index + "0" (16字节对齐)
            String originalAddress = "0X" + blockToReplace.tag + 
                                   Integer.toHexString(cacheIndex) + "0";
            System.out.println("replaceBlockAddress："+originalAddress);
            CPUNode ownerNode = getTargetNode(getMemoryOwner(originalAddress));
            DirectoryEntry dirEntry = ownerNode.directory.get(originalAddress);
            
            if (dirEntry != null) {
            	//System.out.println("222");
                // 如果是SHARED状态
                if (blockToReplace.state == CacheState.SHARED) {
                    // 从共享集中删除该节点
                    dirEntry.sharingSet.remove(node.id);
                    
                    // 如果共享集变空，删除目录项
                    if (dirEntry.sharingSet.isEmpty()) {
                        ownerNode.directory.remove(originalAddress);
                    }
                }
                // 如果是MODIFIED状态（对应目录中的EXCLUSIVE）
                else if (blockToReplace.state == CacheState.MODIFIED) {
                    // 写回存储器
                	//System.out.println("111");
                    writeBackToPrivateMemory(originalAddress, node);
                    // 删除目录项
                    ownerNode.directory.remove(originalAddress);
                    System.out.println("替换块：更新目录表");
                }
            }
            
            // 重置被替换的缓存块状态
            blockToReplace.state = CacheState.INVALID;
        }
        updateDirectoryTabs(); // 更新目录状态显示
    }
    
    /*
     * 处理读请求
     */
    private void handleReadRequest(CPUNode targetNode, CPUNode ownerNode, 
                                 String address, int cacheIndex, boolean cacheHit) {
        CacheBlock block = targetNode.cacheBlocks[cacheIndex];
        if (!cacheHit) {
        	handleCacheReplacement(targetNode, cacheIndex);
            // 缓存缺失处理
            DirectoryEntry dirEntry = ownerNode.directory.computeIfAbsent(address, k -> new DirectoryEntry());
            
            // 根据目录状态处理
            switch (dirEntry.state) {
                case UNCACHED:
                    // 直接从私有存储器读取
                    loadFromPrivateMemory(ownerNode, address, block);
                    dirEntry.state = DirectoryState.SHARED;
                    dirEntry.sharingSet.add(targetNode.id);
                    block.state = CacheState.SHARED;
                    break;
                    
                case SHARED:
                    // 从任一共享节点获取数据
                    String sharingNodeId = dirEntry.sharingSet.iterator().next();
                    CPUNode sharingNode = getTargetNode(sharingNodeId);
                    copyCacheBlock(address, sharingNode, targetNode);
                    dirEntry.sharingSet.add(targetNode.id);
                    block.state = CacheState.SHARED;
                    break;
                    
                case EXCLUSIVE:
                    // 从独占节点获取数据并将目录状态转变为共享
                    String exclusiveNodeId = dirEntry.sharingSet.iterator().next();
                    CPUNode exclusiveNode = getTargetNode(exclusiveNodeId);
                    copyCacheBlock(address, exclusiveNode, targetNode);
                    exclusiveNode.cacheBlocks[getCacheIndex(address)].state = CacheState.SHARED;
                    dirEntry.state = DirectoryState.SHARED;
                    dirEntry.sharingSet.add(targetNode.id);
                    block.state = CacheState.SHARED;
                    break;
            }
        }
    }
    
    /*
     * 处理写请求
     */
    private void handleWriteRequest(CPUNode targetNode, CPUNode ownerNode, 
                                  String address, int cacheIndex, String writeValue, 
                                  boolean cacheHit) {
        CacheBlock block = targetNode.cacheBlocks[cacheIndex];
        DirectoryEntry dirEntry = ownerNode.directory.computeIfAbsent(address, k -> new DirectoryEntry());
        if (!cacheHit) {
        	// 先处理可能的替换
            handleCacheReplacement(targetNode, cacheIndex);
            // 缓存缺失处理
            switch (dirEntry.state) {
                case UNCACHED:
                    // 直接从私有存储器加载并修改
                    loadFromPrivateMemory(ownerNode, address, block);
                    block.state = CacheState.MODIFIED;
                    block.isDirty = true;
                    block.data = writeValue.split("");
                    dirEntry.state = DirectoryState.EXCLUSIVE;
                    dirEntry.sharingSet.add(targetNode.id);
                    break;
                    
                case SHARED:
                    // 作废所有共享副本
                    for (String nodeId : dirEntry.sharingSet) {
                        if (!nodeId.equals(targetNode.id)) {
                            CPUNode node = getTargetNode(nodeId);
                            invalidateCacheBlock(address, node);
                        }
                    }
                    // 从任一共享节点获取数据
                    String sharingNodeId = dirEntry.sharingSet.iterator().next();
                    CPUNode sharingNode = getTargetNode(sharingNodeId);
                    copyCacheBlock(address, sharingNode, targetNode);
                    block.state = CacheState.MODIFIED;
                    block.isDirty = true;
                    block.data = writeValue.split("");
                    dirEntry.state = DirectoryState.EXCLUSIVE;
                    dirEntry.sharingSet.clear();
                    dirEntry.sharingSet.add(targetNode.id);
                    break;
                    
                case EXCLUSIVE:
                    // 写回当前独占副本并获取所有权
                    String exclusiveNodeId = dirEntry.sharingSet.iterator().next();
                    CPUNode exclusiveNode = getTargetNode(exclusiveNodeId);
                    writeBackToPrivateMemory(address, exclusiveNode);
                    copyCacheBlock(address, exclusiveNode, targetNode);
                    block.state = CacheState.MODIFIED;
                    block.isDirty = true;
                    block.data = writeValue.split("");
                    dirEntry.sharingSet.clear();
                    dirEntry.sharingSet.add(targetNode.id);
                    break;
            }
        } else {
            // 缓存命中处理
            switch (dirEntry.state) {
                case SHARED:
                    // 作废所有共享副本
                    for (String nodeId : dirEntry.sharingSet) {
                        if (!nodeId.equals(targetNode.id)) {
                            CPUNode node = getTargetNode(nodeId);
                            invalidateCacheBlock(address, node);
                        }
                    }
                    block.state = CacheState.MODIFIED;
                    block.isDirty = true;
                    block.data = writeValue.split("");
                    dirEntry.state = DirectoryState.EXCLUSIVE;
                    dirEntry.sharingSet.clear();
                    dirEntry.sharingSet.add(targetNode.id);
                    break;
                    
                case EXCLUSIVE:
                    // 已经是独占状态，直接更新
                    block.state = CacheState.MODIFIED;
                    block.isDirty = true;
                    block.data = writeValue.split("");
                    break;
            }
        }
    }
    
    // 从私有存储器加载数据到缓存块
    private void loadFromPrivateMemory(CPUNode ownerNode, String address, CacheBlock block) {
    	 // 获取16字节对齐的块地址
        String blockAddress = address.substring(0, address.length()-1) + "0";
        
        String[] blockData = ownerNode.privateMemory.computeIfAbsent(blockAddress, k -> {
            String[] newBlock = new String[BLOCK_SIZE];
            Arrays.fill(newBlock, "0");
            return newBlock;
        });
        
        
        // 设置缓存块信息
        block.tag = address.substring(2, 6);
        block.index = address.substring(6, 7);
        block.offset = address.substring(7, 8);
        
        System.arraycopy(blockData, 0, block.data, 0, BLOCK_SIZE);
        
    }
    
    // 写回私有存储器
    private void writeBackToPrivateMemory(String address, CPUNode node) {
    	int cacheIndex = getCacheIndex(address);
        CacheBlock block = node.cacheBlocks[cacheIndex];
        if (!block.isDirty) return;
        
        // 获取16字节对齐的块地址
        String blockAddress = address.substring(0, address.length()-1) + "0";
        
        // 确保内存中有该块
        String[] blockData = node.privateMemory.computeIfAbsent(blockAddress, 
            k -> new String[BLOCK_SIZE]);
        
        // 写回数据
        System.arraycopy(block.data, 0, blockData, 0, BLOCK_SIZE);
        block.isDirty = false;
        
        // 输出信息
        System.out.println("----------------------");
        System.out.printf("写回操作 - 节点: %s\n", node.id);
        System.out.printf("内存地址: %s\n", blockAddress);
        System.out.printf("缓存状态: %s -> INVALID\n", block.state);
        System.out.printf("数据内容: %s\n", String.join("", block.data));
        System.out.println("----------------------");
        
        // 更新状态
        block.state = CacheState.INVALID;
    }
    
    // 复制缓存块数据
    private void copyCacheBlock(String address, CPUNode srcNode, CPUNode destNode) {
        int cacheIndex = getCacheIndex(address);
        CacheBlock srcBlock = srcNode.cacheBlocks[cacheIndex];
        CacheBlock destBlock = destNode.cacheBlocks[cacheIndex];
        
        destBlock.tag = srcBlock.tag;
        destBlock.index = srcBlock.index;
        destBlock.offset = srcBlock.offset;
        destBlock.data = srcBlock.data.clone();
    }
    
    // 作废缓存块
    private void invalidateCacheBlock(String address, CPUNode node) {
        int cacheIndex = getCacheIndex(address);
        CacheBlock block = node.cacheBlocks[cacheIndex];
        block.state = CacheState.INVALID;
        //block.tag = "-";
    }
    
    // 获取缓存索引
    private int getCacheIndex(String address) {
        String index = address.substring(6, 7);
        return Integer.parseInt(index, 16);
    }
    
    /*
     * 验证用户输入的有效性
     */
    private boolean validateInput(String address, String operation, String writeValue) {
        // 验证地址格式(24位十六进制)
        if (!address.matches("^0X[0-9A-F]{6}$")) {
            JOptionPane.showMessageDialog(this, "无效地址格式（需为24位十六进制）");
            return false;
        }
        // 验证写入值格式(16字节十六进制)
        if (operation.equals("写") && !writeValue.matches("^[0-9A-F]{16}$")) {
            JOptionPane.showMessageDialog(this, "写入值必须为16字节十六进制");
            return false;
        }
        return true;
    }

    /*
     * 记录内存请求历史
     */
    private void recordRequest(String address, String operation, String targetCpuId, boolean cacheHit) {
        Map<String, Object> request = new HashMap<>();
        request.put("address", address);
        request.put("operation", operation);
        request.put("targetCpu", targetCpuId);
        request.put("cacheHit", cacheHit);
        request.put("ownerNode", getMemoryOwner(address));
        request.put("details", generateOperationDetails(address, operation, targetCpuId, cacheHit));

        // 添加到历史记录的开头(最新记录显示在最前)
        requestHistory.add(0, request);
        // 保持最多10条记录
        if (requestHistory.size() > 10) requestHistory.remove(10);
        updateHistoryList();
    }
    
    // 生成操作详情
    private List<String> generateOperationDetails(String address, String operation, 
                                               String targetCpuId, boolean cacheHit) {
        List<String> details = new ArrayList<>();
        String ownerNode = getMemoryOwner(address);
        
        details.add(String.format("操作类型: %s", operation));
        details.add(String.format("Cache状态: %s", cacheHit ? "命中" : "缺失"));
        details.add(String.format("存储器归属节点: %s", ownerNode));
        
        if (operation.equals("读")) {
            details.add("读取数据到缓存");
            if (!cacheHit) {
                details.add("从节点 " + ownerNode + " 的私有存储器加载数据");
            }
        } else {
            details.add("更新缓存数据");
            if (!cacheHit) {
                details.add("从节点 " + ownerNode + " 获取数据所有权");
            }
            details.add("作废其他节点的缓存副本");
        }
        
        return details;
    }

    /*
     * 更新历史列表显示
     */
    private void updateHistoryList() {
        historyModel.clear();
        requestHistory.forEach(r -> historyModel.addElement(
            String.format("[%s] %s %s %s (归属:%s)",
                (Boolean) r.get("cacheHit") ? "命中" : "缺失",
                r.get("targetCpu"),
                r.get("operation"),
                r.get("address"),
                r.get("ownerNode")
            )
        ));
    }

    /*
     * 重新渲染CPU节点面板
     */
    private void renderCPUNodes() {
        cpuNodesPanel.removeAll();
        for (CPUNode node : cpuNodes) {
            JPanel cpuPanel = new JPanel(new BorderLayout());
            cpuPanel.setBorder(new TitledBorder(node.id));
            cpuPanel.add(new JScrollPane(new JTable(createCacheTableModel(node))), BorderLayout.CENTER);
            cpuNodesPanel.add(cpuPanel);
        }
        cpuNodesPanel.revalidate();
        cpuNodesPanel.repaint();
    }

    /*
     * 显示请求详情
     */
    private void showRequestDetails() {
        int index = historyList.getSelectedIndex();
        if (index == -1) return;

        Map<String, Object> request = requestHistory.get(index);
        StringBuilder details = new StringBuilder();
        details.append("===== 请求详情 =====\n");
        details.append(String.format("地址: %s\n", request.get("address")));
        details.append(String.format("操作: %s\n", request.get("operation")));
        details.append(String.format("目标节点: %s\n", request.get("targetCpu")));
        details.append(String.format("存储器归属节点: %s\n", request.get("ownerNode")));
        details.append(String.format("Cache状态: %s\n", 
            (Boolean)request.get("cacheHit") ? "命中" : "缺失"));
        details.append("\n操作步骤:\n");
        
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) request.get("details");
        for (String step : steps) {
            details.append("• ").append(step).append("\n");
        }
        
        detailArea.setText(details.toString());
    }

    /*
     * 重置系统状态
     */
    private void resetSystem() {
        initSystem();
        historyModel.clear();
        detailArea.setText("");
        renderCPUNodes();
        updateDirectoryTabs();
    }

    /*
     * 根据ID获取目标CPU节点
     */
    private CPUNode getTargetNode(String targetCpuId) {
        return Arrays.stream(cpuNodes)
                .filter(node -> node.id.equals(targetCpuId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("目标节点不存在"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Directory().setVisible(true));
    }
}
