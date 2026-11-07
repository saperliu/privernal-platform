/*
 * Copyright 2025-2026 saperliu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.privernal.dynamic.dbs.util.scan;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-22 11:26
 * @Description
 */
//public class ContainerEntityManagerFactoryBean extends LocalContainerEntityManagerFactoryBean {
//
//    private final DefaultPersistenceUnitManager persistenceUnitManager = new DefaultPersistenceUnitManager();
//
//    private PersistenceUnitInfo           persistenceUnitInfo;
//    private String[]                      packagesToScan;
//    private String                        scanPackages = "";
//    private EntityManagerFactory nativeEntityManagerFactory;
////    @Override
////    public PersistenceUnitInfo getPersistenceUnitInfo() {
////        return this.persistenceUnitInfo;
////    }
//
////    @Override
////    public String getPersistenceUnitName() {// 指定实体工厂使用特定的持久化单元
////        if (this.persistenceUnitInfo != null) {
////            return this.persistenceUnitInfo.getPersistenceUnitName();
////        }
////        return super.getPersistenceUnitName();
////    }
//
//    @Override
//    public DataSource getDataSource() {
//        if (this.persistenceUnitInfo != null)
//            return this.persistenceUnitInfo.getNonJtaDataSource();
//        else
//            return this.persistenceUnitManager.getDefaultDataSource();
//    }
//
//    public  EntityManagerFactory getNewFactory(String scanPackages){
//        this.scanPackages = scanPackages;
//        return this.createNativeEntityManagerFactory();
//
//    }
//
//
//    @Override
//    protected EntityManagerFactory createNativeEntityManagerFactory() throws PersistenceException {
//        PersistenceUnitManager managerToUse = this.persistenceUnitManager;
//        this.persistenceUnitManager.setPackagesToScan(packagesToScan);
//        this.persistenceUnitManager.afterPropertiesSet();
//        managerToUse = this.persistenceUnitManager;
//
//        this.persistenceUnitInfo = determinePersistenceUnitInfo(managerToUse);
//        JpaVendorAdapter jpaVendorAdapter = getJpaVendorAdapter();
//        if (jpaVendorAdapter != null && this.persistenceUnitInfo instanceof SmartPersistenceUnitInfo) {
//            ((SmartPersistenceUnitInfo) this.persistenceUnitInfo).setPersistenceProviderPackageName(
//                    jpaVendorAdapter.getPersistenceProviderRootPackage());
//        }
//        PersistenceProvider provider = getPersistenceProvider();
//        if (logger.isInfoEnabled())
//            logger.info("正在构建 JPA 容器 EntityManagerFactory, 持久化单元为:'" + this.persistenceUnitInfo.getPersistenceUnitName() + "'");
//
////        scanEntitys();// 扫描实体并在创建实体工厂前添加到所使用的持久化单元里
//
//        this.nativeEntityManagerFactory = provider.createContainerEntityManagerFactory(this.persistenceUnitInfo, getJpaPropertyMap());
//
//        postProcessEntityManagerFactory(this.nativeEntityManagerFactory, this.persistenceUnitInfo);
//
//        return this.nativeEntityManagerFactory;
//    }
//
////    @Override
////    protected PersistenceUnitInfo determinePersistenceUnitInfo(PersistenceUnitManager persistenceUnitManager) {
////        if (getPersistenceUnitName() != null)
////            return persistenceUnitManager.obtainPersistenceUnitInfo(getPersistenceUnitName());
////        else
////            return persistenceUnitManager.obtainDefaultPersistenceUnitInfo();
////    }
//    @Override
//    protected void postProcessEntityManagerFactory(EntityManagerFactory emf, PersistenceUnitInfo pui) {
//    }
//
////    public void setPersistenceUnitInfo(PersistenceUnitInfo persistenceUnitInfo) {
////        this.persistenceUnitInfo = persistenceUnitInfo;
////    }
//    @Override
//    public void setDataSource(DataSource dataSource) {
//        this.persistenceUnitManager.setDataSourceLookup(new SingleDataSourceLookup(dataSource));
//        this.persistenceUnitManager.setDefaultDataSource(dataSource);
//    }
//
//    public void setScanPackages(String scanPackages) {// null或不指定表示不扫描,空白表示扫描非jar目录
//        this.scanPackages = scanPackages;
//    }
//    @Override
//    public void setPackagesToScan(String... packagesToScan) {
//        this.packagesToScan = packagesToScan;
//    }
//
//    private void scanEntitys() {
//        String[] pgs = scanPackages.split(",");
//        if (pgs.length > -1) {
//            ClassPathScanner p = new ClassPathScanner();
//            // p.addIncludeFilter(new AssignableTypeFilter(TypeFilter.class));
//            // Set<MetadataReader> bd =
//            // p.findCandidateClasss("org.springframework");
//            p.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
//            Set<MetadataReader> bd = p.findCandidateClasss(pgs);
//            List<String> managedClass = persistenceUnitInfo.getManagedClassNames();
//            for (MetadataReader b : bd) {
//                if (!(managedClass.contains(b.getClassMetadata().getClassName()))) {
//                    managedClass.add(b.getClassMetadata().getClassName());
//                }
//            }
//        }
//    }
//
//}
