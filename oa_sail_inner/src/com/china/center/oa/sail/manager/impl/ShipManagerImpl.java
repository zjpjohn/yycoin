package com.china.center.oa.sail.manager.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.center.china.osgi.config.ConfigLoader;
import com.china.center.jdbc.annosql.constant.AnoConstant;
import com.china.center.oa.client.bean.CustomerBean;
import com.china.center.oa.client.dao.CustomerMainDAO;
import com.china.center.oa.product.bean.CiticVSOAProductBean;
import com.china.center.oa.product.dao.CiticVSOAProductDAO;
import com.china.center.oa.publics.bean.StafferBean;
import com.china.center.oa.publics.dao.StafferDAO;
import com.china.center.oa.publics.manager.CommonMailManager;
import com.china.center.oa.sail.bean.*;
import com.china.center.oa.sail.dao.*;
import com.china.center.oa.sail.vo.BranchRelationVO;
import com.china.center.tools.StringTools;
import jxl.Workbook;
import jxl.format.*;
import jxl.write.*;
import jxl.write.biff.RowsExceededException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.annotation.Transactional;

import com.center.china.osgi.publics.User;
import com.china.center.common.MYException;
import com.china.center.jdbc.util.ConditionParse;
import com.china.center.oa.product.bean.DepotBean;
import com.china.center.oa.product.dao.DepotDAO;
import com.china.center.oa.publics.dao.CommonDAO;
import com.china.center.oa.sail.constanst.OutConstant;
import com.china.center.oa.sail.constanst.ShipConstant;
import com.china.center.oa.sail.manager.ShipManager;
import com.china.center.oa.sail.vo.DistributionVO;
import com.china.center.oa.sail.vo.OutVO;
import com.china.center.oa.sail.vo.PackageVO;
import com.china.center.tools.JudgeTools;
import com.china.center.tools.ListTools;
import com.china.center.tools.TimeTools;

public class ShipManagerImpl implements ShipManager
{
    private final Log operationLog = LogFactory.getLog("opr");

    private final Log triggerLog = LogFactory.getLog("trigger");

    private final Log _logger = LogFactory.getLog(getClass());

    private PreConsignDAO preConsignDAO = null;

    private PackageDAO packageDAO = null;

    private PackageItemDAO packageItemDAO = null;

    private OutDAO outDAO = null;

    private BaseDAO baseDAO = null;

    private DistributionDAO distributionDAO = null;

    private CommonDAO commonDAO = null;

    private DepotDAO depotDAO = null;

    private PackageVSCustomerDAO packageVSCustomerDAO = null;

    private CommonMailManager commonMailManager = null;

    private BranchRelationDAO branchRelationDAO = null;

    private OutImportDAO outImportDAO = null;

    private ConsignDAO consignDAO = null;

    private StafferDAO stafferDAO = null;

    private CustomerMainDAO customerMainDAO = null;

    private CiticVSOAProductDAO citicVSOAProductDAO = null;

    public ShipManagerImpl()
    {
    }

    private void createNewPackage(OutVO outBean,
                                  List<BaseBean> baseList, DistributionVO distVO, String fullAddress, String location)
    {
        System.out.println("***********ShipManager createNewPackage*************");
        String id = commonDAO.getSquenceString20("CK");

        int allAmount = 0;

        PackageBean packBean = new PackageBean();

        packBean.setId(id);
        packBean.setCustomerId(outBean.getCustomerId());
        packBean.setShipping(distVO.getShipping());
        packBean.setTransport1(distVO.getTransport1());
        packBean.setExpressPay(distVO.getExpressPay());
        packBean.setTransport2(distVO.getTransport2());
        packBean.setTransportPay(distVO.getTransportPay());
        packBean.setAddress(fullAddress);
        packBean.setReceiver(distVO.getReceiver());
        packBean.setMobile(distVO.getMobile());
        packBean.setTelephone(distVO.getTelephone());
        packBean.setLocationId(location);
        packBean.setCityId(distVO.getCityId());

        packBean.setStafferName(outBean.getStafferName());
        packBean.setIndustryName(outBean.getIndustryName());
        packBean.setDepartName(outBean.getIndustryName3());

        packBean.setTotal(outBean.getTotal());
        packBean.setStatus(0);
        packBean.setLogTime(TimeTools.now());

        List<PackageItemBean> itemList = new ArrayList<PackageItemBean>();

        Map<String, List<BaseBean>> pmap = new HashMap<String, List<BaseBean>>();

        boolean isEmergency = false;
        for (BaseBean base : baseList)
        {
            PackageItemBean item = new PackageItemBean();

            item.setPackageId(id);
            item.setOutId(outBean.getFullId());
            item.setBaseId(base.getId());
            item.setProductId(base.getProductId());
            item.setProductName(base.getProductName());

            //2015/8/12 调拨单产生的CK单的数量应该取绝对值，因为调拨出库是负的数量
            if (outBean.getType() == OutConstant.OUT_TYPE_INBILL
                    && outBean.getOutType() == OutConstant.OUTTYPE_IN_MOVEOUT )
            {
                item.setAmount(Math.abs(base.getAmount()));
                item.setPrice(Math.abs(base.getPrice()));
            } else {
                item.setAmount(base.getAmount());
                item.setPrice(base.getPrice());
            }

            item.setValue(base.getValue());
            item.setOutTime(outBean.getOutTime());
            item.setDescription(outBean.getDescription());
            item.setCustomerId(outBean.getCustomerId());
            item.setEmergency(outBean.getEmergency());

            if (item.getEmergency() == 1) {
                isEmergency = true;
            }

            itemList.add(item);

            allAmount += item.getAmount();

            if (!pmap.containsKey(base.getProductId()))
            {
                List<BaseBean> blist = new ArrayList<BaseBean>();

                blist.add(base);

                pmap.put(base.getProductId(), blist);
            }else
            {
                List<BaseBean> blist = pmap.get(base.getProductId());

                blist.add(base);
            }
        }

        packBean.setAmount(allAmount);

        if (isEmergency) {
            packBean.setEmergency(OutConstant.OUT_EMERGENCY_YES);
        }

        packBean.setProductCount(pmap.values().size());

        PackageVSCustomerBean vsBean = new PackageVSCustomerBean();

        vsBean.setPackageId(id);
        vsBean.setCustomerId(outBean.getCustomerId());
        vsBean.setCustomerName(outBean.getCustomerName());
        vsBean.setIndexPos(1);

        packBean.setPrintInvoiceinsStatus(itemList);
        packageDAO.saveEntityBean(packBean);
        _logger.info(String.format("生成CK单:%s",packBean.getId()));

        packageItemDAO.saveAllEntityBeans(itemList);

        packageVSCustomerDAO.saveEntityBean(vsBean);
    }

    private void setInnerCondition(DistributionVO distVO, String location, ConditionParse con)
    {
        int shipping = distVO.getShipping();
        if (shipping == 0){
            //自提：收货人，电话一致，才合并
            //2015/2/3 仓库地址也必须一致
            con.addCondition("PackageBean.locationId", "=", location);
            con.addCondition("PackageBean.receiver", "=", distVO.getReceiver());
            con.addCondition("PackageBean.mobile", "=", distVO.getMobile());
            con.addIntCondition("PackageBean.status", "=", 0);
        } else if (shipping == 2){
            //第三方快递：地址、收货人、电话完全一致，才合并.能不能判断地址后6个字符一致，电话，收货人一致，就合并
            String fullAddress = distVO.getProvinceName()+distVO.getCityName()+distVO.getAddress();
            String temp = fullAddress.trim();

            //#25 包含特殊字符\,过滤掉
            if (temp.contains("\\")) {
                String[] arrays = temp.split("\\\\");
                _logger.info(arrays);
                int length = arrays.length;
                if (length == 1){
                    String temp2 = arrays[0];
                    con.addCondition("PackageBean.address", "like", "%"+temp2+"%");
                } else{
                    String temp2 = arrays[length-1];
                    con.addCondition("PackageBean.address", "like", "%"+temp2+"%");
                }
            }
            else if (temp.length()>=6){
                con.addCondition("PackageBean.address", "like", "%"+temp.substring(temp.length()-6));
            }else{
                con.addCondition("PackageBean.address", "like", "%"+temp);
            }

            con.addIntCondition("PackageBean.shipping", "=", distVO.getShipping());

            con.addCondition("PackageBean.receiver", "=", distVO.getReceiver());

            con.addCondition("PackageBean.mobile", "=", distVO.getMobile());

            con.addCondition(" and (PackageBean.pickupId ='' or PackageBean.pickupId IS NULL)");
            con.addCondition(" and PackageBean.status in(0,5)");
        } else{
            //con.addCondition("PackageBean.customerId", "=", outBean.getCustomerId());
            con.addCondition("PackageBean.cityId", "=", distVO.getCityId());  // 借用outId 用于存储城市。生成出库单增加 城市 维度

            con.addIntCondition("PackageBean.shipping", "=", distVO.getShipping());

            con.addIntCondition("PackageBean.transport1", "=", distVO.getTransport1());

            con.addIntCondition("PackageBean.expressPay", "=", distVO.getExpressPay());

            con.addIntCondition("PackageBean.transport2", "=", distVO.getTransport2());

            con.addIntCondition("PackageBean.transportPay", "=", distVO.getTransportPay());

            con.addCondition("PackageBean.locationId", "=", location);

            con.addCondition("PackageBean.receiver", "=", distVO.getReceiver());

            con.addCondition("PackageBean.mobile", "=", distVO.getMobile());

            con.addIntCondition("PackageBean.status", "=", 0);
        }

    }

    /**
     *
     */
    public void createPackage(PreConsignBean pre, OutVO out) throws MYException
    {
        String fullId = out.getFullId();
        _logger.info("**************ShipManager createPackage with fullId "+fullId);
        String location = "";

        // 通过仓库获取 仓库地点
        DepotBean depot = depotDAO.find(out.getLocation());

        if (depot != null)
            location = depot.getIndustryId2();

        List<BaseBean> baseList = baseDAO.queryEntityBeansByFK(fullId);

        List<DistributionVO> distList = distributionDAO.queryEntityVOsByFK(fullId);

        if (ListTools.isEmptyOrNull(distList))
        {
            preConsignDAO.deleteEntityBean(pre.getId());

            return;
        }

        DistributionVO distVO = distList.get(0);

        // 如果是空发,则不处理
        if (distVO.getShipping() == OutConstant.OUT_SHIPPING_NOTSHIPPING)
        {
            preConsignDAO.deleteEntityBean(pre.getId());

            return;
        }

        // 地址不全,不发
        if (distVO.getAddress().trim().equals("0") && distVO.getReceiver().trim().equals("0") && distVO.getMobile().trim().equals("0"))
        {
            return;
        }

        String fullAddress = distVO.getProvinceName()+distVO.getCityName()+distVO.getAddress();

        // 此客户是否存在同一个发货包裹,且未拣配
        ConditionParse con = new ConditionParse();

        con.addWhereStr();

        setInnerCondition(distVO, location, con);

        List<PackageVO> packageList = packageDAO.queryVOsByCondition(con);

//		if (packageList.size() > 1){
//			throw new MYException("数据异常,生成发货单出错.");
//		}

        if (ListTools.isEmptyOrNull(packageList))
        {
            createNewPackage(out, baseList, distVO, fullAddress, location);

        }else{
            String id = packageList.get(0).getId();

            PackageBean packBean = packageDAO.find(id);
            _logger.info(fullId+"可能存在相同地址的CK单:"+packBean);

            // 不存在或已不是初始状态(可能已被拣配)
            if (null == packBean || packBean.getStatus() != 0)
            {
                createNewPackage(out, baseList, distVO, fullAddress, location);
            }else
            {
                List<PackageItemBean> itemList = new ArrayList<PackageItemBean>();

                int allAmount = 0;
                double total = 0;

                Map<String, List<BaseBean>> pmap = new HashMap<String, List<BaseBean>>();
                boolean isEmergency = false;
                for (BaseBean base : baseList)
                {
                    PackageItemBean item = new PackageItemBean();

                    item.setPackageId(id);
                    item.setOutId(out.getFullId());
                    item.setBaseId(base.getId());
                    item.setProductId(base.getProductId());
                    item.setProductName(base.getProductName());
                    item.setAmount(base.getAmount());
                    item.setPrice(base.getPrice());
                    item.setValue(base.getValue());
                    item.setOutTime(out.getOutTime());
                    item.setDescription(out.getDescription());
                    item.setCustomerId(out.getCustomerId());
                    item.setEmergency(out.getEmergency());

                    if (item.getEmergency() == 1) {
                        isEmergency = true;
                    }

                    itemList.add(item);

                    allAmount += item.getAmount();
                    total += base.getValue();

                    if (!pmap.containsKey(base.getProductId()))
                    {
                        List<BaseBean> blist = new ArrayList<BaseBean>();

                        blist.add(base);

                        pmap.put(base.getProductId(), blist);
                    }else
                    {
                        List<BaseBean> blist = pmap.get(base.getProductId());

                        blist.add(base);
                    }
                }

                packBean.setAmount(packBean.getAmount() + allAmount);
                packBean.setTotal(packBean.getTotal() + total);
                packBean.setProductCount(packBean.getProductCount() + pmap.values().size());

                if (isEmergency) {
                    packBean.setEmergency(OutConstant.OUT_EMERGENCY_YES);
                }

                packageDAO.updateEntityBean(packBean);

                packageItemDAO.saveAllEntityBeans(itemList);
                _logger.info(fullId+"合并到CK单:"+packBean.getId());
                // 包与客户关系
                PackageVSCustomerBean vsBean = packageVSCustomerDAO.findByUnique(id, out.getCustomerId());

                if (null == vsBean)
                {
                    int count = packageVSCustomerDAO.countByCondition("where packageId = ?", id);

                    PackageVSCustomerBean newvsBean = new PackageVSCustomerBean();

                    newvsBean.setPackageId(id);
                    newvsBean.setCustomerId(out.getCustomerId());
                    newvsBean.setCustomerName(out.getCustomerName());
                    newvsBean.setIndexPos(count + 1);

                    packageVSCustomerDAO.saveEntityBean(newvsBean);
                }
            }
        }

        preConsignDAO.deleteEntityBean(pre.getId());
    }

    @Override
    @Transactional(rollbackFor = MYException.class)
    public void mergePackages(String user, String packageIds, int shipping, int transport1, int transport2, int expressPay, int transportPay, String cityId, String address, String receiver, String phone) throws MYException {
        //To change body of implemented methods use File | Settings | File Templates.
//        JudgeTools.judgeParameterIsNull(user, packageIds);
        JudgeTools.judgeParameterIsNull(packageIds);

        String[] packages = packageIds.split("~");

        List<String> packageList = new ArrayList<String>();
        if (null != packages)
        {
            int i = 1;
            String stafferName = "";

            PackageBean packBean = new PackageBean();
            List<PackageItemBean> itemList = new ArrayList<PackageItemBean>();
            PackageVO firstPack = null;

            int allAmount = 0;
            double total = 0;
            Map<String, List<PackageItemBean>> pmap = new HashMap<String, List<PackageItemBean>>();

            for (String id : packages)
            {
                packageList.add(id);
                // 只能合并初始态的
                PackageBean bean = packageDAO.find(id);

                if (null == bean)
                {
                    throw new MYException("出库单[%s]不存在", id);
                }else if (bean.getStatus() != ShipConstant.SHIP_STATUS_INIT)
                {
                    throw new MYException("[%s]不是初始状态无法合并", id);
                }

                if (i == 1){
                    firstPack = this.packageDAO.findVO(id);
                    stafferName = firstPack.getStafferName();
                }

                //合并时检查CK单对应的承担人是否一致，如果不一致，则不允许合并
                if (!stafferName.equals(bean.getStafferName())){
                    throw new MYException("CK单[%s]对应的承担人不一致", firstPack.getId()+":"+bean.getId());
                }
                i++;
                List<PackageItemBean> items = this.packageItemDAO.queryEntityBeansByFK(bean.getId());

                boolean isEmergency = false;
                if (!ListTools.isEmptyOrNull(items)){
                    for (PackageItemBean item : items){
                        PackageItemBean newItem = new PackageItemBean();

                        //copy current items
                        newItem.setOutId(item.getOutId());
                        newItem.setBaseId(item.getId());
                        newItem.setProductId(item.getProductId());
                        newItem.setProductName(item.getProductName());
                        newItem.setAmount(item.getAmount());
                        newItem.setPrice(item.getPrice());
                        newItem.setValue(item.getValue());
                        newItem.setOutTime(item.getOutTime());
                        newItem.setDescription(item.getDescription());
                        newItem.setCustomerId(item.getCustomerId());
                        newItem.setEmergency(item.getEmergency());

                        if (item.getEmergency() == 1) {
                            isEmergency = true;
                        }

                        itemList.add(item);

                        allAmount += item.getAmount();
                        total += item.getValue();

                        if (!pmap.containsKey(item.getProductId()))
                        {
                            List<PackageItemBean> blist = new ArrayList<PackageItemBean>();

                            blist.add(item);

                            pmap.put(item.getProductId(), blist);
                        }else
                        {
                            List<PackageItemBean> blist = pmap.get(item.getProductId());

                            blist.add(item);
                        }
                    }
                }

                if (isEmergency) {
                    packBean.setEmergency(OutConstant.OUT_EMERGENCY_YES);
                }
            }

            packBean.setAmount(allAmount);
            packBean.setTotal(total);
            packBean.setProductCount(pmap.values().size());

            String id = commonDAO.getSquenceString20("CK");
            String customerId = firstPack.getCustomerId();
            String customerName = firstPack.getCustomerName();

            packBean.setId(id);
            packBean.setCustomerId(customerId);
            packBean.setShipping(shipping);
            packBean.setTransport1(transport1);
            packBean.setTransport2(transport2);
            packBean.setExpressPay(expressPay);
            packBean.setTransportPay(transportPay);
            packBean.setAddress(address);
            packBean.setReceiver(receiver);
            packBean.setMobile(phone);
            packBean.setLocationId(firstPack.getLocationId());
            packBean.setCityId(cityId);
            packBean.setStafferName(firstPack.getStafferName());
            packBean.setIndustryName(firstPack.getIndustryName());
            packBean.setDepartName(firstPack.getDepartName());
            packBean.setStatus(0);
            packBean.setLogTime(TimeTools.now());


            // 包与客户关系
            PackageVSCustomerBean vsBean = packageVSCustomerDAO.findByUnique(id, customerId);

            if (null == vsBean)
            {
                PackageVSCustomerBean newvsBean = new PackageVSCustomerBean();

                newvsBean.setPackageId(id);
                newvsBean.setCustomerId(customerId);
                newvsBean.setCustomerName(customerName);
                newvsBean.setIndexPos(1);

                packageVSCustomerDAO.saveEntityBean(newvsBean);
                _logger.info("***create PackageVSCustomerBean for package***"+id);
            }

            packBean.setPrintInvoiceinsStatus(itemList);
            packageDAO.saveEntityBean(packBean);
            _logger.info(String.format("生成CK单:%s",packBean.getId()));

            for (PackageItemBean item : itemList){
                item.setPackageId(id);
            }
            packageItemDAO.saveAllEntityBeans(itemList);
            _logger.info("***save new merged package items****"+itemList);

            //Delete original packages
            this.packageDAO.deleteByIds(packageList);
            _logger.info("***remove merged packages****"+packageList);
        }

    }

    @Override
    public Map<String, Set<String>> prePickup(User user, String packageIds) throws MYException {
        JudgeTools.judgeParameterIsNull(user, packageIds);

        String [] packages = packageIds.split("~");
        //<key,value> as <receiver or mobile, Set of CK>
        Map<String, Set<String>> map = new HashMap<String,Set<String>>();
        boolean needMerge = false;

        //Find all packages to be pickup
        ConditionParse conditionParse = new ConditionParse();
        conditionParse.addCondition("status", "=", ShipConstant.SHIP_STATUS_INIT);
        List<PackageBean> packagesTodo = this.packageDAO.queryEntityBeansByCondition(conditionParse);
        _logger.info("prePickup check initial package size:"+packagesTodo.size());

        for (String packageId: packages){
            PackageBean bean = packageDAO.find(packageId);
            String receiver = bean.getReceiver();
            String mobile = bean.getMobile();

            // 2015/7/28 点击拣配时检查是否有未捡配CK单与当前捡配的单子是相同收货人或电话但未被合单，弹屏提示CK单号
            if (!ListTools.isEmptyOrNull(packagesTodo)){
                for (PackageBean packageBean : packagesTodo)
                {
                    String id = packageBean.getId();
                    String currentReceiver = packageBean.getReceiver();
                    String currentMobile = packageBean.getMobile();

                    //收货人一致
                    if (!StringTools.isNullOrNone(currentReceiver) && currentReceiver.equals(receiver)){
                        if (map.containsKey(receiver)){
                            Set<String> ckList = map.get(receiver);
                            ckList.add(id);
                            String template = "同一收货人:%s的CK单:%s需要合并";
                            _logger.warn(String.format(template, receiver, id));
                            needMerge = true;
                            continue;
                        } else{
                            Set<String> ckList = new HashSet<String>();
                            ckList.add(id);
                            map.put(receiver, ckList);
                        }
                    }

                    //电话一致
                    if (!StringTools.isNullOrNone(currentMobile) && currentMobile.equals(mobile)){
                        if (map.containsKey(mobile)){
                            Set<String> ckList = map.get(mobile);
                            ckList.add(id);
                            String template = "同一收货电话:%s的CK单:%s需要合并";
                            needMerge = true;
                            _logger.warn(String.format(template, mobile, id));
                        } else{
                            Set<String> ckList = new HashSet<String>();
                            ckList.add(id);
                            map.put(mobile, ckList);
                        }
                    }
                }
            }
        }

        //remove key with single CK
        for (Iterator<Map.Entry<String, Set<String>>> it = map.entrySet().iterator();
             it.hasNext();)
        {
            Map.Entry<String, Set<String>> entry = it.next();
            Set<String> ckList = entry.getValue();
            if(ckList == null || ckList.size() <=1)
            {
                it.remove();
                _logger.info("remove key with single CK:"+entry.getKey());
            }
        }
        return needMerge ? map : null;
    }

    /**
     * 拣配包
     */
    @Transactional(rollbackFor = MYException.class)
    public boolean addPickup(User user, String packageIds) throws MYException
    {
        JudgeTools.judgeParameterIsNull(user, packageIds);

        String [] packages = packageIds.split("~");

        if (null != packages)
        {
            String pickupId = commonDAO.getSquenceString20("PC");

            int i = 1;

            for (String id : packages)
            {
                // 只能拣配初始态的
                PackageBean bean = packageDAO.find(id);

                if (null == bean)
                {
                    throw new MYException("出库单[%s]不存在", id);
                }else if (bean.getStatus() != ShipConstant.SHIP_STATUS_INIT
                        &&bean.getStatus() != ShipConstant.SHIP_STATUS_PRINT_INVOICEINS)
                {
                    throw new MYException("[%s]已被拣配", id);
                }

                bean.setIndex_pos(i++);

                bean.setPickupId(pickupId);

                //#328 如果包含虚拟发票号，就不更新CK单状态
                if (this.containsXnInvoiceins(id)){
                    bean.setStatus(ShipConstant.SHIP_STATUS_PRINT_INVOICEINS);
                } else{
                    bean.setStatus(ShipConstant.SHIP_STATUS_PICKUP);
                }

                bean.setPickupTime(TimeTools.now());
                packageDAO.updateEntityBean(bean);
            }

        }

        return true;
    }

    /**
     *
     *
     * @param packageIds
     * @param pickupCount
     *@param currentPickupCount @return 捡配批次数量
     * @throws MYException
     */
    @Transactional(rollbackFor = MYException.class)
    public List<String> addPickup(String packageIds, int pickupCount, int currentPickupCount) throws MYException {
        _logger.info("***addPickup***");
        String[] packages = packageIds.split("~");

        List<String> pickupIdList = new ArrayList<String>();
        List<PackageBean> pickupList = new ArrayList<PackageBean>();
        if (null != packages)
        {
            _logger.info(packageIds+"*****addPickup with package size****"+packages.length+"***pickupCount***"+pickupCount+"***currentPickupCount***"+currentPickupCount);
            List<String> packageList = Arrays.asList(packages);

            for (String id : packageList)
            {
                if (currentPickupCount+pickupList.size()>=pickupCount){
                    _logger.info("****auto pickup exit when reach max*****");
                    break;
                }
                // 只能拣配初始态的
                PackageBean bean = packageDAO.find(id);

                if (null == bean)
                {
                    throw new MYException("出库单[%s]不存在", id);
                }else if (bean.getStatus() != ShipConstant.SHIP_STATUS_INIT &&
                        bean.getStatus() != ShipConstant.SHIP_STATUS_PRINT_INVOICEINS)
                {
                    throw new MYException("[%s]已被拣配", id);
                }

                PackageBean pickup = this.getPickupId(bean, pickupList);

                bean.setIndex_pos(pickup.getIndex_pos());

                bean.setPickupId(pickup.getPickupId());

                //#328 如果包含虚拟发票号，就不更新CK单状态
                if (this.containsXnInvoiceins(id)){
                    bean.setStatus(ShipConstant.SHIP_STATUS_PRINT_INVOICEINS);
                } else{
                    bean.setStatus(ShipConstant.SHIP_STATUS_PICKUP);
                }

                packageDAO.updateEntityBean(bean);
                _logger.info("update package bean***"+bean);
            }


//            for (int i=0;i<batchCount;i++){
//                List<String> soList = new ArrayList<String>();
//                if (i== batchCount-1){
//                    soList = packageList.subList(i*LIMIT,packageList.size());
//                }   else{
//                    soList = packageList.subList(i*LIMIT,(i+1)*LIMIT);
//                }
//                String pickupId = commonDAO.getSquenceString20("PC");
//                _logger.info("*******create pickupId********"+pickupId);
//                pickupIdList.add(pickupId);
//
//                for (String id : soList)
//                {
//                    // 只能拣配初始态的
//                    PackageBean bean = packageDAO.find(id);
//
//                    if (null == bean)
//                    {
//                        throw new MYException("出库单[%s]不存在", id);
//                    }else if (bean.getStatus() != ShipConstant.SHIP_STATUS_INIT)
//                    {
//                        throw new MYException("[%s]已被拣配", id);
//                    }
//
//                    bean.setIndex_pos(i++);
//
//                    bean.setPickupId(pickupId);
//
//                    bean.setStatus(ShipConstant.SHIP_STATUS_PICKUP);
//
//                    packageDAO.updateEntityBean(bean);
//                    _logger.info(id+"*****update package pickupId****"+pickupId);
//                }
//
//                if (currentPickupCount+1>=pickupCount){
//                    _logger.info("****pickupCount reach max****");
//                    break;
//                }
//            }

        }

        _logger.info("***exit package pickup count****" + pickupList.size());
        for (PackageBean packBean : pickupList){
            pickupIdList.add(packBean.getPickupId());
        }
        return pickupIdList;
    }

    private boolean containsXnInvoiceins(String packageId){
        List<PackageItemBean> packageItemBeanList = this.packageItemDAO.queryEntityBeansByFK(packageId);
        if (!ListTools.isEmptyOrNull(packageItemBeanList)){
            for(PackageItemBean item: packageItemBeanList){
                if (item.getProductName()!= null && item.getProductName().contains("发票号：XN")){
                    return true;
                }
            }
        }
        return false;
    }

    /** 一个批次里的商品总数量不能大于50，如一张CK单的数量超过50，单独为一个批次
     * 2015/3/12
     * @param bean
     * @param pickupList
     * @return
     */
    private PackageBean getPickupId(PackageBean bean,List<PackageBean> pickupList){
        PackageBean packBean = null;
        if (pickupList.size() == 0){
            packBean = new PackageBean();
            packBean.setAmount(bean.getAmount());
            String pickupId = commonDAO.getSquenceString20("PC");
            _logger.info("*******create pickupId********"+pickupId);
            packBean.setPickupId(pickupId);

            pickupList.add(packBean);
        } else{
            packBean = pickupList.get(pickupList.size()-1);
            int amount = packBean.getAmount()+bean.getAmount();
            //一个批次里的商品总数量不能大于50，如一张CK单的数量超过50，单独为一个批次
            final int LIMIT = 2;
            if (amount>LIMIT){
                _logger.info("****product amount reach MAX****");
                packBean = new PackageBean();
                packBean.setAmount(bean.getAmount());
                String pickupId = commonDAO.getSquenceString20("PC");
                _logger.info("*******create pickupId********"+pickupId);
                packBean.setPickupId(pickupId);

                pickupList.add(packBean);
            } else{
                packBean.setIndex_pos(packBean.getIndex_pos()+1);
                packBean.setAmount(amount);
            }
        }

        return packBean;
    }

    @Override
    @Transactional(rollbackFor = MYException.class)
    public boolean cancelPackage(User user, String packageIds) throws MYException {
        JudgeTools.judgeParameterIsNull(user, packageIds);

        String [] packages = packageIds.split("~");

        if (null != packages)
        {
            for (String id : packages)
            {
                // “已拣配”与“已打印”状态的CK单可以撤销
                PackageBean bean = packageDAO.find(id);

                if (null == bean)
                {
                    throw new MYException("出库单[%s]不存在", id);
                } else {
                    int status = bean.getStatus();
                    if (status == ShipConstant.SHIP_STATUS_PICKUP || status == ShipConstant.SHIP_STATUS_PRINT){
                        bean.setStatus(ShipConstant.SHIP_STATUS_INIT);
                        bean.setPickupId("");
                        this.packageDAO.updateEntityBean(bean);
                        _logger.info("**********cancelPackage now*****"+id);
                    } else if (status == ShipConstant.SHIP_STATUS_PRINT_INVOICEINS){
                        bean.setStatus(ShipConstant.SHIP_STATUS_PRINT_INVOICEINS);
                        bean.setPickupId("");
                        this.packageDAO.updateEntityBean(bean);
                        _logger.info("**********cancelPackage now*****"+id);
                    }  else if (status == ShipConstant.SHIP_STATUS_CONSIGN){
                        throw new MYException("出库单[%s]已发货不能撤销捡配", id);
                    }
                }
            }
        }

        return true;
    }

    @Transactional(rollbackFor = MYException.class)
    public boolean deletePackage(User user, String packageIds)
            throws MYException
    {
        JudgeTools.judgeParameterIsNull(user, packageIds);

        String [] packages = packageIds.split("~");

        if (null != packages)
        {
            Set<String> set = new HashSet<String>();

            for (String id : packages)
            {
                // 只能拣配初始态的
                PackageBean bean = packageDAO.find(id);

                if (null == bean)
                {
                    throw new MYException("出库单[%s]不存在", id);
                }

                packageDAO.deleteEntityBean(bean.getId());

                List<PackageItemBean> itemList = packageItemDAO.queryEntityBeansByFK(bean.getId());

                for (PackageItemBean each : itemList)
                {
                    if (!set.contains(each.getOutId()))
                    {
                        set.add(each.getOutId());
                    }

                    packageItemDAO.deleteEntityBean(each.getId());
                }

                packageVSCustomerDAO.deleteEntityBeansByFK(id);
            }

            // 重新生成发货单
            for (String outId : set)
            {
                PreConsignBean pre = new PreConsignBean();

                pre.setOutId(outId);

                preConsignDAO.saveEntityBean(pre);
                this.logPreconsign(pre);
            }

            operationLog.info("重新生成发货单准备数据PreConsign:" + set);
        }

        return true;
    }

    private void logPreconsign(PreConsignBean preConsignBean){
        String message = String.format("生成preconsign表:%s", preConsignBean.getOutId());
        _logger.info(message);
    }

    /**
     *
     */
    @Transactional(rollbackFor = MYException.class)
    public boolean updatePrintStatus(String pickupId, int index_pos) throws MYException
    {
        JudgeTools.judgeParameterIsNull(pickupId);

        ConditionParse condtion = new ConditionParse();

        condtion.addWhereStr();

        condtion.addCondition("PackageBean.pickupId", "=", pickupId);
        condtion.addIntCondition("PackageBean.index_pos", "=", index_pos);

        List<PackageBean> packageList = packageDAO.queryEntityBeansByCondition(condtion);

        if (!ListTools.isEmptyOrNull(packageList))
        {
            PackageBean packageBean = packageList.get(0);

            if (packageBean.getStatus() != ShipConstant.SHIP_STATUS_CONSIGN)
            {
                packageBean.setStatus(ShipConstant.SHIP_STATUS_PRINT);
                packageBean.setPrintTime(TimeTools.now());

                packageDAO.updateEntityBean(packageBean);
                _logger.info(packageBean.getId() + " status updated to " + ShipConstant.SHIP_STATUS_PRINT);
            }
        }

        return true;
    }

    @Transactional(rollbackFor = MYException.class)
    public boolean updateStatus(User user, String pickupId) throws MYException
    {
        JudgeTools.judgeParameterIsNull(user, pickupId);

        List<PackageBean> packageList = packageDAO.queryEntityBeansByFK(pickupId);
        this.checkEmptyTransportNo(packageList);

        Set<String> set = new HashSet<String>();

        for (PackageBean each : packageList)
        {
            if (StringTools.isNullOrNone(each.getPickupId())){
                _logger.info("****CK单pickupId不能为空****"+each.getId());
                throw new MYException("CK单[%s]pickupId不能为空", each.getId());
            }
            each.setStatus(ShipConstant.SHIP_STATUS_CONSIGN);

            each.setShipTime(TimeTools.now());

            List<PackageItemBean> itemList = packageItemDAO.queryEntityBeansByFK(each.getId());

            for (PackageItemBean eachItem : itemList)
            {
                if (!set.contains(eachItem.getOutId()))
                {
                    OutBean out = outDAO.find(eachItem.getOutId());

                    if (null != out && out.getStatus() == OutConstant.STATUS_PASS)
                    {
                        outDAO.modifyOutStatus(out.getFullId(), OutConstant.STATUS_SEC_PASS);

                        distributionDAO.updateOutboundDate(out.getFullId(), TimeTools.now_short());
                    }
                }
            }

            packageDAO.updateEntityBean(each);
        }

        return true;
    }

    //2015/3/4 确认发货时要检查发货方式为第三方快递的CK单的快递单号不能为空
    private void checkEmptyTransportNo(List<PackageBean> packageList) throws MYException{
        for (PackageBean each : packageList)
        {
            if (StringTools.isNullOrNone(each.getTransportNo())){
                throw new MYException("CK单[%s]的快递单号transportNo不能为空", each.getId());
            }
//            List<PackageItemBean> itemList = packageItemDAO.queryEntityBeansByFK(each.getId());
//
//            for (PackageItemBean eachItem : itemList)
//            {
//                String outId = eachItem.getOutId();
//                OutBean out = outDAO.find(outId);
//                if (out!= null){
//                    List<DistributionBean> distList = distributionDAO.queryEntityBeansByFK(outId);
//                    if (!ListTools.isEmptyOrNull(distList)){
//                        DistributionBean distBean = distList.get(0);
//                        //发货方式为第三方快递
//                        if (distBean.getShipping() == 2){
//                            // 获取发货单号
//                            String distId = distBean.getId();
//                            ConsignBean consign = consignDAO.findDefaultConsignByDistId(distId);
//                            if (consign == null || StringTools.isNullOrNone(consign.getTransportNo())){
//                                _logger.info(each.getId()+":"+distId+" no ConsignBean related with SO:"+outId);
//                                throw new MYException("第三方快递的CK单[%s]的快递单号不能为空", each.getId()+":"+outId);
//                            }
//                        }
//                    }
//                }
//            }
        }
    }

    @Override
    @Transactional(rollbackFor = MYException.class)
    public boolean updatePackagesStatus(User user, String packageIds) throws MYException {
        JudgeTools.judgeParameterIsNull(user, packageIds);

        List<PackageBean> packageList = new ArrayList<PackageBean>();
        String [] packages = packageIds.split("~");

        if (packages!= null){
            for (String pack : packages){
                PackageBean bean = this.packageDAO.find(pack);
                if (bean!= null){
                    packageList.add(bean);
                }
            }
        }

        this.checkEmptyTransportNo(packageList);

        Set<String> set = new HashSet<String>();
        for (PackageBean each : packageList)
        {
            if (StringTools.isNullOrNone(each.getPickupId())){
                _logger.info("****CK单pickupId不能为空****"+each.getId());
                throw new MYException("CK单[%s]pickupId不能为空", each.getId());
            }
            each.setStatus(ShipConstant.SHIP_STATUS_CONSIGN);

            each.setShipTime(TimeTools.now());

            List<PackageItemBean> itemList = packageItemDAO.queryEntityBeansByFK(each.getId());

            for (PackageItemBean eachItem : itemList)
            {
                if (!set.contains(eachItem.getOutId()))
                {
                    OutBean out = outDAO.find(eachItem.getOutId());

                    if (null != out && out.getStatus() == OutConstant.STATUS_PASS)
                    {
                        outDAO.modifyOutStatus(out.getFullId(), OutConstant.STATUS_SEC_PASS);

                        distributionDAO.updateOutboundDate(out.getFullId(), TimeTools.now_short());
                    }
                }
            }

            packageDAO.updateEntityBean(each);
        }

        return true;
    }

    @Deprecated
    @Transactional(rollbackFor = MYException.class)
    public void sendMailForShipping2() throws MYException {
        //To change body of implemented methods use File | Settings | File Templates.
        long now = System.currentTimeMillis();
        System.out.println("**************run schedule****************" + now);

        ConditionParse con = new ConditionParse();
        con.addWhereStr();
        con.addIntCondition("PackageBean.sendMailFlag", "=", 0);
        con.addIntCondition("PackageBean.status", "=", 2);

        List<PackageVO> packageList = packageDAO.queryVOsByCondition(con);
        if (!ListTools.isEmptyOrNull(packageList))
        {
            for (PackageVO vo : packageList){
                System.out.println("************VO****"+vo);
                //First query 分支行对应关系表
                ConditionParse con2 = new ConditionParse();
                con2.addWhereStr();
                con2.addCondition("BranchRelationBean.id", "=", vo.getCustomerId());
                List<BranchRelationVO> relationList = this.branchRelationDAO.queryVOsByCondition(con2);
                if (!ListTools.isEmptyOrNull(relationList)){
                    BranchRelationVO relation = relationList.get(0);
                    System.out.println("**********relation******"+relation);

                    String fileName = getShippingAttachmentPath() + "/" + vo.getCustomerId()
                            + "_" + TimeTools.now("yyyyMMddHHmmss") + ".xls";
                    System.out.println("************fileName****"+fileName);

                    String title = String.format("永银文化%s发货信息", this.getYesterday());
                    String content = "永银文化创意产业发展有限责任公司发货信息，请查看附件，谢谢。";
                    if(relation.getSendMailFlag() == 1){
                        createMailAttachment2(vo,relation.getBranchName(), fileName);

                        // check file either exists
                        File file = new File(fileName);

                        if (!file.exists())
                        {
                            throw new MYException("邮件附件未成功生成");
                        }

                        // send mail contain attachment
                        commonMailManager.sendMail(relation.getSubBranchMail(), title,content, fileName);

                        //Update sendMailFlag to 1
                        PackageBean packBean = packageDAO.find(vo.getId());
                        packBean.setSendMailFlag(1);
                        this.packageDAO.updateEntityBean(packBean);
                    }

                    if(relation.getCopyToBranchFlag() == 1){
                        // 抄送分行
                        commonMailManager.sendMail(relation.getBranchMail(), title,content, fileName);
                    }

                }
            }
        } else {
            System.out.println("**************no Vo found***************");
        }

    }

    /**
     * see #146: 宁波银行邮件信息(发货前)
     *  每天17点，统计当天截止时间t_center_package表中
     * 邮件发送标识为空，且status字段值为2的，且客户名称为 宁波银行XXXX    的CK单
     * @throws MYException
     */
    @Override
    @Transactional(rollbackFor = MYException.class)
    public void sendMailForNbBeforeShipping() throws MYException {
        //To change body of implemented methods use File | Settings | File Templates.
        String msg =  "**************run sendMailForNbShipping job****************";
        triggerLog.info(msg);
        _logger.info(msg);

        ConditionParse con = new ConditionParse();
        con.addWhereStr();
        con.addIntCondition("PackageBean.sendMailFlagNbyh", "=", 0);
        con.addCondition("CustomerBean.name", "like", "宁波银行%");

        List<PackageVO> packageList = packageDAO.queryVOsByCondition(con);
        if (!ListTools.isEmptyOrNull(packageList))
        {
            _logger.info("****NB packageList to be sent mail***"+packageList.size());


            //send mail for merged packages
            {
                String fileName = getShippingAttachmentPath() + "/宁波银行"
                        + "_" + TimeTools.now("yyyyMMddHHmmss") + ".xls";
                _logger.info("************fileName****"+fileName);

                String title = String.format("永银文化%s发货信息", this.getYesterday());
                String content = "永银文化创意产业发展有限责任公司发货信息，请查看附件，谢谢。";
                {
                    createNbMailAttachment(packageList, fileName);

                    // check file either exists
                    File file = new File(fileName);

                    if (!file.exists())
                    {
                        throw new MYException("邮件附件未成功生成");
                    }

                    commonMailManager.sendMail("zhousudong@yycoin.com", title,content, fileName);
                }
            }
        } else {
            _logger.info("*****no VO found to send mail****");
        }
    }

    /**
     * see #146: 宁波银行邮件信息(已发货)
     *  每天23点，统计当天截止时间t_center_package表中
     * 邮件发送标识为空，且status字段值为2的，且客户名称为 宁波银行XXXX    的CK单
     * @throws MYException
     */
    @Override
    @Transactional(rollbackFor = MYException.class)
    public void sendMailForNbShipping() throws MYException {
        //To change body of implemented methods use File | Settings | File Templates.
        String msg =  "**************run sendMailForNbShipping job****************";
        triggerLog.info(msg);
        _logger.info(msg);

        ConditionParse con = new ConditionParse();
        con.addWhereStr();
        con.addIntCondition("PackageBean.sendMailFlagNbyh", "=", 0);
        con.addIntCondition("PackageBean.status", "=", 2);
        con.addCondition("CustomerBean.name", "like", "宁波银行%");

        List<PackageVO> packageList = packageDAO.queryVOsByCondition(con);
        if (!ListTools.isEmptyOrNull(packageList))
        {
            _logger.info("****NB packageList to be sent mail***"+packageList.size());


            //send mail for merged packages
            {
                String fileName = getShippingAttachmentPath() + "/宁波银行"
                        + "_" + TimeTools.now("yyyyMMddHHmmss") + ".xls";
                _logger.info("************fileName****"+fileName);

                String title = String.format("永银文化%s发货信息", this.getYesterday());
                String content = "永银文化创意产业发展有限责任公司发货信息，请查看附件，谢谢。";
                {
                    createNbMailAttachment(packageList, fileName);

                    // check file either exists
                    File file = new File(fileName);

                    if (!file.exists())
                    {
                        throw new MYException("邮件附件未成功生成");
                    }

                    commonMailManager.sendMail("wangjiayi@nbcb.cn", title,content, fileName);
                    commonMailManager.sendMail("359985066@qq.com", title,content, fileName);
//                    commonMailManager.sendMail("zhousudong@yycoin.com", title,content, fileName);
                }

                //Update sendMailFlagNbyh to 1
                for (PackageBean vo: packageList){
                    PackageBean packBean = packageDAO.find(vo.getId());
                    packBean.setSendMailFlagNbyh(1);
                    this.packageDAO.updateEntityBean(packBean);
                    _logger.info("update package setSendMailFlagNbyh status:"+vo.getId());
                }
            }
        } else {
            _logger.info("*****no VO found to send mail****");
        }
    }

    private void createNbMailAttachment(List<PackageVO> beans, String fileName)
    {
        WritableWorkbook wwb = null;

        WritableSheet ws = null;

        OutputStream out = null;

        try
        {
            out = new FileOutputStream(fileName);

            // create a excel
            wwb = Workbook.createWorkbook(out);

            ws = wwb.createSheet("发货信息", 0);

            // 横向
            ws.setPageSetup(PageOrientation.LANDSCAPE.LANDSCAPE, PaperSize.A4,0.5d,0.5d);

            // 标题字体
            WritableFont font = new WritableFont(WritableFont.ARIAL, 11,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font2 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font3 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.NO_BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font4 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLUE);

            WritableCellFormat format = new WritableCellFormat(font);

            format.setAlignment(jxl.format.Alignment.CENTRE);
            format.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);

            WritableCellFormat format2 = new WritableCellFormat(font2);

            format2.setAlignment(jxl.format.Alignment.LEFT);
            format2.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);
            format2.setWrap(true);

            WritableCellFormat format21 = new WritableCellFormat(font2);
            format21.setAlignment(jxl.format.Alignment.RIGHT);

            WritableCellFormat format3 = new WritableCellFormat(font3);
            format3.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);

            WritableCellFormat format31 = new WritableCellFormat(font3);
            format31.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
            format31.setAlignment(jxl.format.Alignment.RIGHT);

            WritableCellFormat format4 = new WritableCellFormat(font4);
            format4.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);

            WritableCellFormat format41 = new WritableCellFormat(font4);
            format41.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
            format41.setAlignment(jxl.format.Alignment.CENTRE);
            format41.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);

            int i = 0, j = 0, i1 = 1;
            String title = String.format("永银文化%s发货信息", this.getYesterday());

            // 完成标题
            ws.addCell(new Label(1, i, title, format));

            //set column width
            ws.setColumnView(0, 30);
            ws.setColumnView(1, 20);
            ws.setColumnView(2, 40);
            ws.setColumnView(3, 40);
            ws.setColumnView(4, 40);
            ws.setColumnView(5, 20);
            ws.setColumnView(6, 20);
            ws.setColumnView(7, 10);
            ws.setColumnView(8, 30);
            ws.setColumnView(9, 10);
            ws.setColumnView(10, 10);

            i++;
            // 正文表格
            ws.addCell(new Label(0, i, "供应商id", format3));
            ws.addCell(new Label(1, i, "供应商名称", format3));
            ws.addCell(new Label(2, i, "机构id", format3));
            ws.addCell(new Label(3, i, "机构名称", format3));
            ws.addCell(new Label(4, i, "发货单号（供应商提供）", format3));
            ws.addCell(new Label(5, i, "订单编号", format3));
            ws.addCell(new Label(6, i, "快递单号", format3));
            ws.addCell(new Label(7, i, "产品id", format3));
            ws.addCell(new Label(8, i, "产品名称", format3));
            ws.addCell(new Label(9, i, "产品类型", format3));
            ws.addCell(new Label(10, i, "产品数量", format3));

            for (PackageVO bean :beans){
                List<PackageItemBean> itemList = packageItemDAO.queryEntityBeansByFK(bean.getId());
                if (!ListTools.isEmptyOrNull(itemList)){
                    for (PackageItemBean each : itemList)
                    {
                        i++;
//						ws.addCell(new Label(j++, i, String.valueOf(i1++), format3));
//						setWS(ws, i, 300, false);

                        //供应商id
                        ws.addCell(new Label(j++, i, "2015120215260025", format3));
                        //供应商名称
                        ws.addCell(new Label(j++, i, "永银文化", format3));

                        ConditionParse con3 = new ConditionParse();
                        con3.addWhereStr();
                        con3.addCondition("OutImportBean.oano", "=", each.getOutId());
                        List<OutImportBean> importBeans = this.outImportDAO.queryEntityBeansByCondition(con3);
                        OutImportBean importBean = new OutImportBean();
                        if (!ListTools.isEmptyOrNull(importBeans)){
                            importBean = importBeans.get(0);
                        }

                        //机构id
                        ws.addCell(new Label(j++, i, importBean.getComunicationBranch(), format3));

                        //机构名称
                        ws.addCell(new Label(j++, i, importBean.getComunicatonBranchName(), format3));

                        String transportNo = "";
                        List<ConsignBean> consignBeans = this.consignDAO.queryConsignByFullId(each.getOutId());
                        if (!ListTools.isEmptyOrNull(consignBeans)){
                            ConsignBean b = consignBeans.get(0);
                            if (!StringTools.isNullOrNone(b.getTransportNo())){
                                transportNo = b.getTransportNo();
                            }
                        }

                        //发货单号
                        ws.addCell(new Label(j++, i, transportNo, format3));

                        //订单编号
                        ws.addCell(new Label(j++, i, importBean.getNbyhNo(), format3));

                        //快递单号
                        ws.addCell(new Label(j++, i, transportNo, format3));

                        //产品id
                        ws.addCell(new Label(j++, i, each.getProductCode(), format3));

                        //产品名称
                        ws.addCell(new Label(j++, i, each.getProductName(), format3));

                        //产品类型
                        ws.addCell(new Label(j++, i, "工艺金", format3));

                        //产品数量
                        ws.addCell(new Label(j++, i, String.valueOf(each.getAmount()), format3));

                        j = 0;
//                        i++;
                    }
                }
            }


        }
        catch (Throwable e){
            e.printStackTrace();
        }
        finally
        {
            if (wwb != null)
            {
                try
                {
                    wwb.write();
                    wwb.close();
                }
                catch (Exception e1)
                {
                }
            }
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (IOException e1)
                {
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = MYException.class)
    public void sendShippingMailToSails() throws MYException {
        //To change body of implemented methods use File | Settings | File Templates.
        String msg =  "**************run sendShippingMailToSails job****************";
        triggerLog.info(msg);

        ConditionParse con = new ConditionParse();
        con.addWhereStr();
        con.addIntCondition("PackageBean.sendMailFlagSails", "=", 0);
        con.addCondition("PackageBean.logTime", ">=", "2016-04-27 00:00:00");
        con.addIntCondition("PackageBean.status", "=", 2);
        //自提类的也不在发送邮件范围内
        con.addIntCondition("PackageBean.shipping","!=", 0);

        //根据销售人员合并CK单:<staffer邮件,List<订单>>
        Map<String,List<PackageVO>> staffer2Packages = new HashMap<String,List<PackageVO>>();
        Map<String,String> mail2Name = new HashMap<String,String>();

        List<PackageVO> packageList = packageDAO.queryVOsByCondition(con);
        if (!ListTools.isEmptyOrNull(packageList))
        {
            _logger.info("****packageList to be sent to sails***"+packageList.size());
            for (PackageVO vo : packageList){
                StafferBean stafferBean = this.stafferDAO.findyStafferByName(vo.getStafferName());
                if (stafferBean!= null){
                    String mail = stafferBean.getNation();
                    if (!StringTools.isNullOrNone(mail)){
                        if (staffer2Packages.containsKey(mail)){
                            List<PackageVO> voList = staffer2Packages.get(mail);
                            voList.add(vo);
                        }else{
                            List<PackageVO> voList =  new ArrayList<PackageVO>();
                            voList.add(vo);
                            staffer2Packages.put(mail, voList);
                        }
                        mail2Name.put(mail, stafferBean.getName());
                    }else{
                        _logger.warn("***no mail for***"+vo.getStafferName());
                    }
                }
            }

            //send mail for merged packages
            for (String mail : staffer2Packages.keySet()) {
                List<PackageVO> packages = staffer2Packages.get(mail);
                String fileName = getShippingAttachmentPath() + "/" + "发货邮件"+"_"
                        + mail2Name.get(mail)+"_" + TimeTools.now("yyyyMMddHHmmss") + ".xls";

                String title = String.format("永银文化%s发货信息", this.getYesterday());
                String content = "永银文化创意产业发展有限责任公司发货信息，请查看附件，谢谢。";
                createMailAttachment(packages,"" , fileName, false);

                // check file either exists
                File file = new File(fileName);

                if (!file.exists())
                {
                    throw new MYException("邮件附件未成功生成");
                }

                //#228 发送销售人员
                commonMailManager.sendMail(mail, title,content, fileName);

                StringBuilder sb = new StringBuilder();
                for (PackageBean vo:packages){
                    //Update sendMailFlagSails to 1
                    PackageBean packBean = packageDAO.find(vo.getId());
                    packBean.setSendMailFlagSails(1);
                    this.packageDAO.updateEntityBean(packBean);
                    _logger.info("***update mail flag***"+vo.getId());
                    sb.append(vo.getId()+",");
                }

                _logger.info(fileName+"***sent to mail***"+mail+"with packages***"+sb.toString());
            }
        } else {
            _logger.info("*****no VO found to send mail****");
        }
        triggerLog.info("***finish sendMailToSails***");
    }

    //#2 每日十点发货后必须将已发货信息发送至相关支行与分行
    @Override
    @Transactional(rollbackFor = MYException.class)
    public void sendMailForShipping() throws MYException {
        //To change body of implemented methods use File | Settings | File Templates.
        String msg =  "**************run sendMailForShipping job****************";
        triggerLog.info(msg);

        ConditionParse con = new ConditionParse();
        con.addWhereStr();
        con.addIntCondition("PackageBean.sendMailFlag", "=", 0);
        con.addCondition("PackageBean.logTime", ">=", "2016-04-27 00:00:00");
        con.addIntCondition("PackageBean.status", "=", 2);
        //自提类的也不在发送邮件范围内
        con.addIntCondition("PackageBean.shipping","!=", 0);

        //根据customerId合并CK表:<支行customerId,List<CK>>
        Map<String,List<PackageVO>> customerId2Packages = new HashMap<String,List<PackageVO>>();
        //<支行customerId,BranchRelationBean>
        Map<String,BranchRelationBean> customerId2Relation = new HashMap<String,BranchRelationBean>();

        //step1: 根据支行customerId对CK单合并
        List<PackageVO> packageList = packageDAO.queryVOsByCondition(con);
        if (!ListTools.isEmptyOrNull(packageList))
        {
            triggerLog.info("****packageList to be sent mail to bank***"+packageList.size());
            for (PackageVO vo : packageList){
                //2016/4/12 update
                //如果收货人姓名是在oastaffer表的name字段里的，则此销售单不在发送邮件范围内
                String receiver = vo.getReceiver();
                if(!StringTools.isNullOrNone(receiver)){
                    StafferBean stafferBean = this.stafferDAO.findyStafferByName(receiver);
                    if (stafferBean!= null){
                        _logger.warn(vo.getId()+"***belong to staffer***"+receiver);
                        continue;
                    }
                }

                String customerId = vo.getCustomerId();
                //查询分支行对应关系表
                BranchRelationBean bean = this.getRelationByCustomerId(customerId);
                if(bean == null){
                    _logger.warn(vo.getId()+"***no relation found***"+customerId);
                    continue;
                } else{
                    _logger.info("***relation found****"+bean);
                    customerId2Relation.put(customerId, bean);
                }

                if (customerId2Packages.containsKey(customerId)){
                    List<PackageVO> voList = customerId2Packages.get(customerId);
                    voList.add(vo);
                }else{
                    List<PackageVO> voList =  new ArrayList<PackageVO>();
                    voList.add(vo);
                    customerId2Packages.put(customerId, voList);
                }
            }

            //step2 send mail for merged packages
            triggerLog.info("***mail count to be sent to bank***" + customerId2Packages.keySet().size());
            for (String customerId : customerId2Packages.keySet()) {
                List<PackageVO> packages = customerId2Packages.get(customerId);
                BranchRelationBean bean = customerId2Relation.get(customerId);
                _logger.info(customerId+"***send mail to branch***"+bean);
                if (bean == null){
                    continue;
                } else if (bean.getSendMailFlag() ==0 && bean.getCopyToBranchFlag() == 0) {
                    _logger.warn("***flag is not set***"+bean);
                    continue;
                }
                String subBranch = bean.getSubBranchName();
                String fileName = getShippingAttachmentPath() + "/" + subBranch
                        + "_" + TimeTools.now("yyyyMMddHHmmss") + ".xls";
                _logger.info("***fileName***"+fileName);
                createMailAttachment(packages,bean.getBranchName(), fileName, true);
                // check file either exists
                File file = new File(fileName);
                if (!file.exists())
                {
                    throw new MYException("邮件附件未成功生成");
                }

                String title = String.format("永银文化%s发货信息", this.getYesterday());
                String content = "永银文化创意产业发展有限责任公司发货信息，请查看附件，谢谢。";
                if(bean.getSendMailFlag() == 1){
                    String subBranchMail = bean.getSubBranchMail();
                    _logger.info("***send mail to subBranchMail:"+subBranchMail+"***package size***:"+packages.size());
                    // 发送给支行
                    commonMailManager.sendMail(subBranchMail, title,content, fileName);
                } else{
                    _logger.warn("***send mail flag for sub branch is 0***" + bean);
                }

                if(bean.getCopyToBranchFlag() == 1){
                    String branchMail = bean.getBranchMail();
                    _logger.info("***send mail to branchMail:"+branchMail+"***package size***:"+packages.size());
                    // 抄送分行
                    commonMailManager.sendMail(branchMail, title,content, fileName);
                } else{
                    _logger.warn("***relation send mail copy flag is 0***" + bean);
                }

                for (PackageBean vo:customerId2Packages.get(customerId)){
                    //Update sendMailFlag to 1
                    PackageBean packBean = packageDAO.find(vo.getId());
                    packBean.setSendMailFlag(1);
                    this.packageDAO.updateEntityBean(packBean);
                    _logger.info("***update mail flag for bank***"+vo.getId());
                }
            }
        } else {
            _logger.warn("***no VO found to send mail****");
        }
        triggerLog.info("***finish send mail to bank***");
    }

    private String getYesterday(){
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String specifiedDay = sdf.format(date);
        return this.getSpecifiedDayBefore(specifiedDay);
    }

    private BranchRelationBean getRelationByCustomerId(String customerId){
        ConditionParse con2 = new ConditionParse();
        con2.addWhereStr();
        con2.addCondition("BranchRelationBean.id", "=", customerId);
        List<BranchRelationVO> relationList = this.branchRelationDAO.queryVOsByCondition(con2);
        if (!ListTools.isEmptyOrNull(relationList)){
            BranchRelationVO relation = relationList.get(0);
            _logger.info("***relation found****"+relation);
            return relation;
        } else{
            _logger.warn("***no relation found***"+customerId);
            return null;
        }
    }

    /**
     * 获得指定日期的前一天
     *
     * @param specifiedDay
     * @return
     * @throws Exception
     */
    private String getSpecifiedDayBefore(String specifiedDay) {
        Calendar c = Calendar.getInstance();
        Date date = null;
        try {
            date = new SimpleDateFormat("yy-MM-dd").parse(specifiedDay);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        c.setTime(date);
        int day = c.get(Calendar.DATE);
        c.set(Calendar.DATE, day - 1);

        String dayBefore = new SimpleDateFormat("MM月dd日").format(c
                .getTime());
        return dayBefore;
    }

    /**
     * @return the mailAttchmentPath
     */
    public String getShippingAttachmentPath()
    {
        return ConfigLoader.getProperty("shippingAttachmentPath");
    }

    private void createMailAttachment(List<PackageVO> beans, String branchName, String fileName, boolean ignoreLyOrders)
    {
        _logger.info("***create mail attachment with package "+beans.size()+"***branch***"+branchName+"***file name***"+fileName);
        WritableWorkbook wwb = null;

        WritableSheet ws = null;

        OutputStream out = null;

        try
        {
            out = new FileOutputStream(fileName);

            // create a excel
            wwb = Workbook.createWorkbook(out);

            ws = wwb.createSheet("发货信息", 0);

            // 横向
            ws.setPageSetup(PageOrientation.LANDSCAPE.LANDSCAPE, PaperSize.A4,0.5d,0.5d);

            // 标题字体
            WritableFont font = new WritableFont(WritableFont.ARIAL, 11,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font2 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font3 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.NO_BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font4 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLUE);

            WritableCellFormat format = new WritableCellFormat(font);

            format.setAlignment(jxl.format.Alignment.CENTRE);
            format.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);

            WritableCellFormat format2 = new WritableCellFormat(font2);

            format2.setAlignment(jxl.format.Alignment.LEFT);
            format2.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);
            format2.setWrap(true);

            WritableCellFormat format21 = new WritableCellFormat(font2);
            format21.setAlignment(jxl.format.Alignment.RIGHT);

            WritableCellFormat format3 = new WritableCellFormat(font3);
            format3.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);

            WritableCellFormat format31 = new WritableCellFormat(font3);
            format31.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
            format31.setAlignment(jxl.format.Alignment.RIGHT);

            WritableCellFormat format4 = new WritableCellFormat(font4);
            format4.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);

            WritableCellFormat format41 = new WritableCellFormat(font4);
            format41.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
            format41.setAlignment(jxl.format.Alignment.CENTRE);
            format41.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);

            int i = 0, j = 0, i1 = 1;
            String title = String.format("永银文化%s发货信息", this.getYesterday());

            // 完成标题
            ws.addCell(new Label(1, i, title, format));

            //set column width
            ws.setColumnView(0, 5);
            ws.setColumnView(1, 40);
            ws.setColumnView(2, 40);
            ws.setColumnView(3, 40);
            ws.setColumnView(4, 5);
            ws.setColumnView(5, 30);
            ws.setColumnView(6, 10);
            ws.setColumnView(7, 20);
            ws.setColumnView(8, 10);
            ws.setColumnView(9, 10);

            i++;
            // 正文表格
            ws.addCell(new Label(0, i, "序号", format3));
            ws.addCell(new Label(1, i, "分行名称", format3));
            ws.addCell(new Label(2, i, "支行名称", format3));
            ws.addCell(new Label(3, i, "产品名称", format3));
            ws.addCell(new Label(4, i, "数量", format3));
            ws.addCell(new Label(5, i, "银行订单号", format3));
            ws.addCell(new Label(6, i, "收货人", format3));
            ws.addCell(new Label(7, i, "快递单号", format3));
            ws.addCell(new Label(8, i, "快递公司", format3));
            ws.addCell(new Label(9, i, "发货时间", format3));
            ws.addCell(new Label(10, i, "银行产品编码", format3));

            for (PackageVO bean :beans){
                List<PackageItemBean> itemList = packageItemDAO.queryEntityBeansByFK(bean.getId());
                _logger.info("***itemList size***"+itemList.size());
                if (!ListTools.isEmptyOrNull(itemList)){
                    PackageItemBean first = itemList.get(0);
                    first.getOutId();
                    ConditionParse con3 = new ConditionParse();
                    con3.addWhereStr();
                    con3.addCondition("OutImportBean.oano", "=", first.getOutId());
                    List<OutImportBean> importBeans = this.outImportDAO.queryEntityBeansByCondition(con3);
                    String citicNo = "";
                    if (!ListTools.isEmptyOrNull(importBeans)){
                        for (OutImportBean b: importBeans){
                            if (!StringTools.isNullOrNone(b.getCiticNo())){
                                citicNo = b.getCiticNo();
                            }
                        }
                    }


                    for (PackageItemBean each : itemList)
                    {
                        //#351 filter LY orders
                        if (ignoreLyOrders && each.getOutId().startsWith("LY")){
                            continue;
                        }
                        i++;
                        ws.addCell(new Label(j++, i, String.valueOf(i1++), format3));
                        setWS(ws, i, 300, false);

                        //分行名称
                        if (StringTools.isNullOrNone(branchName)){
                            String customerId = each.getCustomerId();
                            if (StringTools.isNullOrNone(customerId)){
                                ws.addCell(new Label(j++, i, branchName, format3));
                            } else{
                                BranchRelationBean relation = this.getRelationByCustomerId(customerId);
                                if (relation == null){
                                    ws.addCell(new Label(j++, i, branchName, format3));
                                }else{
                                    ws.addCell(new Label(j++, i, relation.getBranchName(), format3));
                                }
                            }
                        } else{
                            ws.addCell(new Label(j++, i, branchName, format3));
                        }

                        //支行名称
                        String customerId = each.getCustomerId();
                        if (StringTools.isNullOrNone(customerId)){
                            ws.addCell(new Label(j++, i, bean.getCustomerName(), format3));
                        } else{
                            CustomerBean customerBean = this.customerMainDAO.find(customerId);
                            if (customerBean == null){
                                ws.addCell(new Label(j++, i, bean.getCustomerName(), format3));
                            } else {
                                ws.addCell(new Label(j++, i, customerBean.getName(), format3));
                            }
                        }

                        //产品名称
                        ws.addCell(new Label(j++, i, this.getProductName(each), format3));
                        //数量
                        ws.addCell(new Label(j++, i, String.valueOf(each.getAmount()), format3));
                        //银行订单号
                        ws.addCell(new Label(j++, i, citicNo, format3));
                        //收货人
                        ws.addCell(new Label(j++, i, bean.getReceiver(), format3));

                        //2016/4/5 #2 快递单号改取package表的transportNo
                        String transportNo = bean.getTransportNo();
                        ws.addCell(new Label(j++, i, transportNo, format3));

                        //快递公司
                        ws.addCell(new Label(j++, i, bean.getTransportName1(), format3));
                        //发货时间默认为前1天
                        ws.addCell(new Label(j++, i, this.getYesterday(), format3));

                        //银行产品编码，逻辑同回执单的逻辑
                        ws.addCell(new Label(j++, i, this.getProductCode(each), format3));

                        j = 0;
                    }
                }
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (wwb != null)
            {
                try
                {
                    wwb.write();
                    wwb.close();
                }
                catch (Exception e1)
                {
                }
            }
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (IOException e1)
                {
                }
            }
        }
    }

    private String getProductCode(PackageItemBean item){
        String productId = "";
        String outId = item.getOutId();
        if (StringTools.isNullOrNone(outId)){
            _logger.warn("****Empty OutId***********"+outId);
        }else if (outId.startsWith("SO")){
            productId = this.getProductCodeFromOutImport(outId);
        } else if(outId.startsWith("ZS")){
            OutBean out = outDAO.find(outId);
            if (out!= null){
                String sourceOutId = out.getRefOutFullId();
                if (!StringTools.isNullOrNone(sourceOutId)){
                    productId = this.getProductCodeFromOutImport(outId);
                }
            }
        }

        return productId;
    }

    private String getProductCodeFromOutImport(String outId){
        String productCode = "";
        ConditionParse conditionParse = new ConditionParse();
        conditionParse.addWhereStr();
        conditionParse.addCondition("OANo", "=", outId);
        List<OutImportBean> importBeans = outImportDAO.queryEntityBeansByCondition(conditionParse);

        if (!ListTools.isEmptyOrNull(importBeans))
        {
            for (OutImportBean outImportBean: importBeans){
                if (!StringTools.isNullOrNone(outImportBean.getProductCode())){
                    productCode = outImportBean.getProductCode();
                    break;
                }
            }
        }
        _logger.info(productCode+"getProductCodeFromOutImport****"+conditionParse.toString());
        return productCode;
    }

    @Deprecated
    private void createMailAttachment2(PackageVO bean, String branchName, String fileName)
    {
        WritableWorkbook wwb = null;

        WritableSheet ws = null;

        OutputStream out = null;

        try
        {
            out = new FileOutputStream(fileName);

            // create a excel
            wwb = Workbook.createWorkbook(out);

            ws = wwb.createSheet("发货信息", 0);

            // 横向
            ws.setPageSetup(PageOrientation.LANDSCAPE.LANDSCAPE, PaperSize.A4,0.5d,0.5d);

            // 标题字体
            WritableFont font = new WritableFont(WritableFont.ARIAL, 11,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font2 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font3 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.NO_BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLACK);

            WritableFont font4 = new WritableFont(WritableFont.ARIAL, 9,
                    WritableFont.BOLD, false,
                    jxl.format.UnderlineStyle.NO_UNDERLINE,
                    jxl.format.Colour.BLUE);

            WritableCellFormat format = new WritableCellFormat(font);

            format.setAlignment(jxl.format.Alignment.CENTRE);
            format.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);

            WritableCellFormat format2 = new WritableCellFormat(font2);

            format2.setAlignment(jxl.format.Alignment.LEFT);
            format2.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);
            format2.setWrap(true);

            WritableCellFormat format21 = new WritableCellFormat(font2);
            format21.setAlignment(jxl.format.Alignment.RIGHT);

            WritableCellFormat format3 = new WritableCellFormat(font3);
            format3.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);

            WritableCellFormat format31 = new WritableCellFormat(font3);
            format31.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
            format31.setAlignment(jxl.format.Alignment.RIGHT);

            WritableCellFormat format4 = new WritableCellFormat(font4);
            format4.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);

            WritableCellFormat format41 = new WritableCellFormat(font4);
            format41.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
            format41.setAlignment(jxl.format.Alignment.CENTRE);
            format41.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);

            int i = 0, j = 0, i1 = 1;

            // 完成标题
            ws.addCell(new Label(0, i, "发货信息", format));

//            setWS(ws, i, 800, true);

            //set column width
            ws.setColumnView(0, 5);
            ws.setColumnView(1, 40);
            ws.setColumnView(2, 40);
            ws.setColumnView(3, 40);
            ws.setColumnView(4, 5);
            ws.setColumnView(5, 30);
            ws.setColumnView(6, 10);
            ws.setColumnView(7, 20);
            ws.setColumnView(8, 10);
            ws.setColumnView(9, 10);

            i++;
            // 正文表格
            ws.addCell(new Label(0, i, "序号", format3));
            ws.addCell(new Label(1, i, "分行名称", format3));
            ws.addCell(new Label(2, i, "支行名称", format3));
            ws.addCell(new Label(3, i, "产品名称", format3));
            ws.addCell(new Label(4, i, "数量", format3));
            ws.addCell(new Label(5, i, "银行订单号", format3));
            ws.addCell(new Label(6, i, "收货人", format3));
            ws.addCell(new Label(7, i, "快递单号", format3));
            ws.addCell(new Label(8, i, "快递公司", format3));
            ws.addCell(new Label(9, i, "发货时间", format3));


            List<PackageItemBean> itemList = packageItemDAO.queryEntityBeansByFK(bean.getId());
            if (!ListTools.isEmptyOrNull(itemList)){
                System.out.println("package itemlist size***********"+itemList.size());

                i++;
                PackageItemBean first = itemList.get(0);
                first.getOutId();
                ConditionParse con3 = new ConditionParse();
                con3.addWhereStr();
                con3.addCondition("OutImportBean.oano", "=", first.getOutId());
                List<OutImportBean> importBeans = this.outImportDAO.queryEntityBeansByCondition(con3);
                String citicNo = "";
                if (!ListTools.isEmptyOrNull(importBeans)){
                    OutImportBean b = importBeans.get(0);
                    citicNo = b.getCiticNo();
                }

                for (PackageItemBean each : itemList)
                {
                    ws.addCell(new Label(j++, i, String.valueOf(i1++), format3));
                    setWS(ws, i, 300, false);

                    //分行名称
                    ws.addCell(new Label(j++, i, branchName, format3));
                    //支行名称
                    ws.addCell(new Label(j++, i, bean.getCustomerName(), format3));
                    //产品名称
                    ws.addCell(new Label(j++, i, each.getProductName(), format3));
                    //数量
                    ws.addCell(new Label(j++, i, String.valueOf(each.getAmount()), format3));
                    //银行订单号
                    ws.addCell(new Label(j++, i, citicNo, format3));
                    //收货人
                    ws.addCell(new Label(j++, i, bean.getReceiver(), format3));
                    //快递单号
                    String transportNo = "";
                    List<ConsignBean> consignBeans = this.consignDAO.queryConsignByFullId(each.getOutId());
                    if (!ListTools.isEmptyOrNull(consignBeans)){
                        ConsignBean b = consignBeans.get(0);
                        if (!StringTools.isNullOrNone(b.getTransportNo())){
                            transportNo = b.getTransportNo();
                        }
                    }
                    ws.addCell(new Label(j++, i, transportNo, format3));
                    //快递公司
                    ws.addCell(new Label(j++, i, bean.getTransportName1(), format3));
                    //发货时间,如没有默认为前1天
                    List<DistributionVO> distList = distributionDAO.queryEntityVOsByFK(each.getOutId());
                    if (ListTools.isEmptyOrNull(distList)){
                        ws.addCell(new Label(j++, i, this.getYesterday(), format3));
                    } else{
                        String outboundDate = distList.get(0).getOutboundDate();
                        if (StringTools.isNullOrNone(outboundDate)){
                            ws.addCell(new Label(j++, i, this.getYesterday(), format3));
                        } else{
                            ws.addCell(new Label(j++, i, distList.get(0).getOutboundDate(), format3));
                        }
                    }

                    j = 0;
                    i++;
                }
            }

        }
        catch (Throwable e)
        {
//            _logger.error(e, e);
            e.printStackTrace();
        }
        finally
        {
            if (wwb != null)
            {
                try
                {
                    wwb.write();
                    wwb.close();
                }
                catch (Exception e1)
                {
                }
            }
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (IOException e1)
                {
                }
            }
        }
    }

    private void setWS(WritableSheet ws, int i, int rowHeight, boolean mergeCell)
            throws WriteException, RowsExceededException
    {
        if (mergeCell) ws.mergeCells(0, i, 9, i);

        ws.setRowView(i, rowHeight);
    }

    @Override
    @Transactional(rollbackFor = MYException.class)
    public void saveAllEntityBeans(List<BranchRelationBean> branchRelationBeans) throws MYException {
        //To change body of implemented methods use File | Settings | File Templates.
        for (BranchRelationBean bean : branchRelationBeans){
            BranchRelationBean beanInDb = this.branchRelationDAO.find(bean.getId());
            if (beanInDb == null){
                this.branchRelationDAO.saveEntityBean(bean);
            } else{
                this.branchRelationDAO.updateEntityBean(bean);
            }
        }
//        this.branchRelationDAO.saveAllEntityBeans(branchRelationBeans);
    }

    @Override
    @Transactional(rollbackFor = MYException.class)
    public List<String> autoPickup(int pickupCount, String productName) throws MYException {
        //To change body of implemented methods use File | Settings | File Templates.
        _logger.info("***autoPickup****"+pickupCount+":"+productName);
        ConditionParse condtion = new ConditionParse();
        condtion.addWhereStr();
        condtion.addIntCondition("status","=", ShipConstant.SHIP_STATUS_INIT);


        String temp = condtion.toString();
//        _logger.info("****************temp************"+temp);
        if (!StringTools.isNullOrNone(productName)){
            StringBuilder sb = new StringBuilder();
            sb.append(temp)
                    .append(" and exists (select PackageItemBean.id from t_center_package_item PackageItemBean where PackageItemBean.packageId=PackageBean.id and PackageItemBean.productName like '%")
                    .append(productName)
                    .append("%')")
                    .append(" order by PackageBean.logTime asc");

            condtion.setCondition(sb.toString());
        }

        List<PackageBean> packages = this.packageDAO.queryEntityBeansByCondition(condtion);
        List<String> pickupIdList = new ArrayList<String>();

        if (!ListTools.isEmptyOrNull(packages)){
            _logger.info("****packages size****"+packages.size());

            //紧急的最优先
            StringBuilder sb1 = new StringBuilder();
            for (Iterator<PackageBean> it = packages.iterator();it.hasNext();){
                PackageBean current = it.next();
                if (current.getEmergency() == 1){
                    String ck = current.getId();
                    _logger.info("*****getEmergency***"+ck);
                    it.remove();
                    sb1.append(ck).append("~");
                }
            }
            String emergencyPackages = sb1.toString();
            _logger.info("****packages size after remove emergency****"+packages.size());
            if (!StringTools.isNullOrNone(emergencyPackages)){
                List<String> pickupList = this.addPickup(emergencyPackages, pickupCount, pickupIdList.size());
                pickupIdList.addAll(pickupList);
                if (pickupIdList.size()>= pickupCount){
                    return pickupIdList;
                }
            }

            //发货方式为“自提”类的CK单拣配在一个批次里
            StringBuilder sb2 = new StringBuilder();
            for (Iterator<PackageBean> it = packages.iterator();it.hasNext();){
                PackageBean current = it.next();
                if (current.getShipping() == 0){
                    String ck = current.getId();
                    _logger.info("*****selfTakePackages***"+ck);
                    it.remove();
                    sb2.append(ck).append("~");
                }
            }
            _logger.info("****packages size after remove selfTakePackages****"+packages.size());
            String selfTakePackages = sb2.toString();
            if (!StringTools.isNullOrNone(selfTakePackages)){
                List<String> pickupList = this.addPickup(selfTakePackages, pickupCount, pickupIdList.size());
                pickupIdList.addAll(pickupList);
                if (pickupIdList.size()>= pickupCount){
                    return pickupIdList;
                }
            }

            //仓库地点相同的在一个批次里
            //<key,value> as <location,packageIds>
            Map<String,StringBuilder> map1 = new HashMap<String,StringBuilder>();

            for (Iterator<PackageBean> it = packages.iterator();it.hasNext();){
                PackageBean current = it.next();
                List<PackageItemBean> items = this.packageItemDAO.queryEntityBeansByFK(current.getId());
                if (!ListTools.isEmptyOrNull(items)){
                    PackageItemBean first = items.get(0);
                    OutVO outBean = outDAO.findVO(first.getOutId());
                    if (outBean!= null){
                        String lo = outBean.getLocation();
                        if (!StringTools.isNullOrNone(lo)){
                            if (map1.containsKey(lo)){
                                StringBuilder sb = map1.get(lo);
                                sb.append(current.getId()).append("~");
                            } else {
                                StringBuilder sb = new StringBuilder();
                                sb.append(current.getId()).append("~");
                                map1.put(lo, sb);
                            }
                        }
                    }
                }
            }

            //remove from queue
            for (String location : map1.keySet()){
                StringBuilder sb = map1.get(location);
                String[] packageIds = sb.toString().split("~");
                _logger.info("****location***packageIds***"+location+":"+sb.toString());
                //只有仓库相同的CK单才合并
                if (packageIds.length >1){
                    for (Iterator<PackageBean> it = packages.iterator();it.hasNext();){
                        PackageBean current = it.next();
                        for (String id : packageIds){
                            if (id.equals(current.getId())){
                                it.remove();
                            }
                        }
                    }

                    List<String> pickupList = this.addPickup(sb.toString(), pickupCount, pickupIdList.size());
                    pickupIdList.addAll(pickupList);
                    if (pickupIdList.size()>= pickupCount){
                        return pickupIdList;
                    }
                }
            }
            _logger.info("****packages size after remove same location****"+packages.size());

            //同一事业部的CK单在同一批次里
            Map<String,StringBuilder> map2 = new HashMap<String,StringBuilder>();
            for (Iterator<PackageBean> it = packages.iterator();it.hasNext();){
                PackageBean current = it.next();
                String industryName = current.getIndustryName();
                if (map2.containsKey(industryName)){
                    map2.get(industryName).append(current.getId()+"~");
                } else{
                    StringBuilder sb = new StringBuilder();
                    sb.append(current.getId()).append("~");
                    map2.put(industryName,sb);
                }
            }
            for (StringBuilder sb :map2.values()){
                List<String> pickupList = this.addPickup(sb.toString(), pickupCount, pickupIdList.size());
                pickupIdList.addAll(pickupList);
                if (pickupIdList.size()>= pickupCount){
                    return pickupIdList;
                }
            }

            _logger.info("****autoPickup exit with pickup count:"+pickupIdList.size());
        } else {
            _logger.info("****autoPickup no packages to do****");
        }

        return pickupIdList;
    }

    @Override
    @Transactional(rollbackFor = MYException.class)
    public void sortPackagesJob() throws MYException {
        _logger.info("**********sortPackagesJob running*************");
        ConditionParse con1 = new ConditionParse();
        con1.addWhereStr();
        con1.addCondition(" and status in (0,5)");
        List<PackageBean> packages = this.packageDAO.queryEntityBeansByCondition(con1);

        if (!ListTools.isEmptyOrNull(packages)){
//            _logger.info("****sortPackagesJob with packages size****"+packages.size());
            for (PackageBean packBean : packages){
                List<PackageItemBean> items = this.packageItemDAO.queryEntityBeansByFK(packBean.getId());
                if (!ListTools.isEmptyOrNull(items)){
                    //取CK单中距当前时间最长的单据创建时间
                    Collections.sort(items, new Comparator<PackageItemBean>() {
                        public int compare(PackageItemBean o1, PackageItemBean o2) {
                            return o2.getOutTime().compareTo(o1.getOutTime());
                        }
                    });
//                    _logger.info(packBean.getId()+" the last item********"+items.get(0).getOutTime());
//                    _logger.info(packBean.getId()+" the first item********"+items.get(items.size()-1).getOutTime());
                    packBean.setBillsTime(items.get(items.size()-1).getOutTime());

                    //2015/3/22 发票单发指CK单中只有A或FP开头的单号，没有其他类型的订单
                    int count = 0;
                    for (PackageItemBean item : items){
                        String outId = item.getOutId();
                        if (outId.startsWith("A") || outId.startsWith("FP")){
                            count++;
                        }else{
                            break;
                        }
                    }
                    if (count == items.size()){
//                        _logger.info(packBean.getId()+" set to INVOICE_SHIP_ALONE****");
                        packBean.setInsFollowOut(ShipConstant.INVOICE_SHIP_ALONE);
                    } else{
//                        _logger.info(packBean.getId()+" set to INVOICE_SHIP_FOLLOW_OUT****");
                        packBean.setInsFollowOut(ShipConstant.INVOICE_SHIP_FOLLOW_OUT);
                    }

                    int count2 = 0;
                    for (PackageItemBean item : items){
                        String outId = item.getOutId();
                        if (outId.startsWith("ZS")){
                            count2++;
                        }else{
                            break;
                        }
                    }
                    if (count2 == items.size()){
//                        _logger.info(packBean.getId()+" set to ZS_SHIP_ALONE****");
                        packBean.setZsFollowOut(ShipConstant.ZS_SHIP_ALONE);
                    } else{
//                        _logger.info(packBean.getId()+" set to ZS_SHIP_FOLLOW_OUT****");
                        packBean.setZsFollowOut(ShipConstant.ZS_SHIP_FOLLOW_OUT);
                    }
                    this.packageDAO.updateEntityBean(packBean);
                    _logger.info(" 更新PackageBean:"+packBean);
                }
            }

        }
    }

    @Override
    public String getProductName(PackageItemBean item) {
            String productName = "";
            String outId = item.getOutId();
            List<OutImportBean> importBeans = outImportDAO.queryEntityBeansByFK(outId, AnoConstant.FK_FIRST);

            if (!ListTools.isEmptyOrNull(importBeans))
            {
                OutImportBean bean = importBeans.get(0);
                String productCode = bean.getProductCode();
                if (!StringTools.isNullOrNone(productCode)){
                    ConditionParse conditionParse =  new ConditionParse();
                    conditionParse.addCondition("citicProductCode", "=", productCode);
                    List<CiticVSOAProductBean> beans = this.citicVSOAProductDAO.queryEntityBeansByCondition(conditionParse);
                    if (!ListTools.isEmptyOrNull(beans)){
                        productName = beans.get(0).getCiticProductName();
                        _logger.info("***getCiticProductName***"+productName);
                    }
                }
            }

            //default pick from package item table
            if (StringTools.isNullOrNone(productName)){
                productName = this.getProductName2(item.getProductName());
            }

            return productName;
    }

    private String getProductName2(String original){
        String name = "";
        try {
            String[] l1 = original.split(" ");
            if (l1.length == 1) {
                name = original;
            } else {
                String word = l1[1];
                String[] l2 = word.split("（");
                if (l2.length == 1) {
                    name = word;
                } else {
                    name = l2[0];
                }
            }
        }catch(Exception e){
            name = original;
        }

        return name;
    }

    @Override
    @Transactional(rollbackFor = MYException.class)
    public void updateShipping(String packageId, DistributionBean distributionBean) {
        _logger.info(packageId+"***update shipping "+distributionBean);
        PackageBean packageBean = this.packageDAO.find(packageId);
        if (packageBean!= null){
            packageBean.setShipping(distributionBean.getShipping());
            if (distributionBean.getShipping() == OutConstant.OUT_SHIPPING_3PL
                    || distributionBean.getShipping() == OutConstant.OUT_SHIPPING_PROXY){
                packageBean.setExpressPay(distributionBean.getExpressPay());
                packageBean.setTransport1(distributionBean.getTransport1());
            } else if (distributionBean.getShipping() == OutConstant.OUT_SHIPPING_TRANSPORT){
                packageBean.setTransportPay(distributionBean.getTransportPay());
                packageBean.setTransport2(distributionBean.getTransport2());
            } else if (distributionBean.getShipping() == OutConstant.OUT_SHIPPING_3PLANDDTRANSPORT){
                packageBean.setExpressPay(distributionBean.getExpressPay());
                packageBean.setTransport1(distributionBean.getTransport1());
                packageBean.setTransportPay(distributionBean.getTransportPay());
                packageBean.setTransport2(distributionBean.getTransport2());
            }

            this.packageDAO.updateEntityBean(packageBean);
            _logger.info("update packageBean ***" + packageBean);
        }

        List<PackageItemBean> itemList = packageItemDAO.queryEntityBeansByFK(packageId);
        for (PackageItemBean item : itemList){
            List<DistributionBean> distList = distributionDAO.queryEntityBeansByFK(item.getOutId());
            if (!ListTools.isEmptyOrNull(distList)){
                for (DistributionBean dist : distList){
                    dist.setShipping(distributionBean.getShipping());
                    dist.setExpressPay(distributionBean.getExpressPay());
                    dist.setTransport1(distributionBean.getTransport1());
                    dist.setTransportPay(distributionBean.getTransportPay());
                    dist.setTransport2(distributionBean.getTransport2());
                    this.distributionDAO.updateEntityBean(dist);
                    _logger.info("***update distribution***"+dist);
                }
            }
        }
    }

    /**
     * @return the preConsignDAO
     */
    public PreConsignDAO getPreConsignDAO()
    {
        return preConsignDAO;
    }

    /**
     * @param preConsignDAO the preConsignDAO to set
     */
    public void setPreConsignDAO(PreConsignDAO preConsignDAO)
    {
        this.preConsignDAO = preConsignDAO;
    }

    /**
     * @return the packageDAO
     */
    public PackageDAO getPackageDAO()
    {
        return packageDAO;
    }

    /**
     * @param packageDAO the packageDAO to set
     */
    public void setPackageDAO(PackageDAO packageDAO)
    {
        this.packageDAO = packageDAO;
    }

    /**
     * @return the packageItemDAO
     */
    public PackageItemDAO getPackageItemDAO()
    {
        return packageItemDAO;
    }

    /**
     * @param packageItemDAO the packageItemDAO to set
     */
    public void setPackageItemDAO(PackageItemDAO packageItemDAO)
    {
        this.packageItemDAO = packageItemDAO;
    }

    /**
     * @return the outDAO
     */
    public OutDAO getOutDAO()
    {
        return outDAO;
    }

    /**
     * @param outDAO the outDAO to set
     */
    public void setOutDAO(OutDAO outDAO)
    {
        this.outDAO = outDAO;
    }

    /**
     * @return the baseDAO
     */
    public BaseDAO getBaseDAO()
    {
        return baseDAO;
    }

    /**
     * @param baseDAO the baseDAO to set
     */
    public void setBaseDAO(BaseDAO baseDAO)
    {
        this.baseDAO = baseDAO;
    }

    /**
     * @return the distributionDAO
     */
    public DistributionDAO getDistributionDAO()
    {
        return distributionDAO;
    }

    /**
     * @param distributionDAO the distributionDAO to set
     */
    public void setDistributionDAO(DistributionDAO distributionDAO)
    {
        this.distributionDAO = distributionDAO;
    }

    /**
     * @return the commonDAO
     */
    public CommonDAO getCommonDAO()
    {
        return commonDAO;
    }

    /**
     * @param commonDAO the commonDAO to set
     */
    public void setCommonDAO(CommonDAO commonDAO)
    {
        this.commonDAO = commonDAO;
    }

    /**
     * @return the depotDAO
     */
    public DepotDAO getDepotDAO()
    {
        return depotDAO;
    }

    /**
     * @param depotDAO the depotDAO to set
     */
    public void setDepotDAO(DepotDAO depotDAO)
    {
        this.depotDAO = depotDAO;
    }

    /**
     * @return the packageVSCustomerDAO
     */
    public PackageVSCustomerDAO getPackageVSCustomerDAO()
    {
        return packageVSCustomerDAO;
    }

    /**
     * @param packageVSCustomerDAO the packageVSCustomerDAO to set
     */
    public void setPackageVSCustomerDAO(PackageVSCustomerDAO packageVSCustomerDAO)
    {
        this.packageVSCustomerDAO = packageVSCustomerDAO;
    }

    public CommonMailManager getCommonMailManager() {
        return commonMailManager;
    }

    public void setCommonMailManager(CommonMailManager commonMailManager) {
        this.commonMailManager = commonMailManager;
    }

    public BranchRelationDAO getBranchRelationDAO() {
        return branchRelationDAO;
    }

    public void setBranchRelationDAO(BranchRelationDAO branchRelationDAO) {
        this.branchRelationDAO = branchRelationDAO;
    }

    public OutImportDAO getOutImportDAO() {
        return outImportDAO;
    }

    public void setOutImportDAO(OutImportDAO outImportDAO) {
        this.outImportDAO = outImportDAO;
    }

    public ConsignDAO getConsignDAO() {
        return consignDAO;
    }

    public void setConsignDAO(ConsignDAO consignDAO) {
        this.consignDAO = consignDAO;
    }

    public StafferDAO getStafferDAO() {
        return stafferDAO;
    }

    public void setStafferDAO(StafferDAO stafferDAO) {
        this.stafferDAO = stafferDAO;
    }

    public CiticVSOAProductDAO getCiticVSOAProductDAO() {
        return citicVSOAProductDAO;
    }

    public void setCiticVSOAProductDAO(CiticVSOAProductDAO citicVSOAProductDAO) {
        this.citicVSOAProductDAO = citicVSOAProductDAO;
    }

    public CustomerMainDAO getCustomerMainDAO() {
        return customerMainDAO;
    }

    public void setCustomerMainDAO(CustomerMainDAO customerMainDAO) {
        this.customerMainDAO = customerMainDAO;
    }
}