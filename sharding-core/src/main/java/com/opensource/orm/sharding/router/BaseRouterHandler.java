package com.opensource.orm.sharding.router;

import java.util.LinkedList;
import java.util.List;

import javax.sql.DataSource;

import com.opensource.orm.sharding.DatabaseTarget;
import com.opensource.orm.sharding.config.ConfigurationManager;
import com.opensource.orm.sharding.config.DefaultConfigurationManager;
import com.opensource.orm.sharding.config.TableConfig;
import com.opensource.orm.sharding.hash.HashGenerator;
import com.opensource.orm.sharding.hash.HashGeneratorFactory;
import com.opensource.orm.sharding.hash.ShardContext;
import com.opensource.orm.sharding.info.ShardingInfo;
import com.opensource.orm.sharding.loadbalance.LoadBalancer;
import com.opensource.orm.sharding.loadbalance.RoundRobinLoadBalancer;
import com.opensource.orm.sharding.utils.ArrayUtils;

public abstract class BaseRouterHandler implements RouterHandler {
	private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

	public List<DatabaseTarget> handle(ShardingInfo shardInfo,
			Object... parameters) {
		if (getConfigurationManager().getShardedTables().contains(
				shardInfo.getTableName())) {
			return this.handleToSpecifyWithTableName(shardInfo, parameters);
		}
		if (ArrayUtils.isEmpty(shardInfo.getHashColumns())) {
			return this.handleToAll(shardInfo, parameters);
		}
		return this.handleToSpecify(shardInfo, parameters);
	}

	public HashGenerator getHashGenerator(ShardingInfo shardInfo) {
		TableConfig tableConfig = getConfigurationManager().getTableConfig(
				shardInfo.getTableName());
		return HashGeneratorFactory.create(tableConfig);
	}

	public ConfigurationManager getConfigurationManager() {
		return DefaultConfigurationManager.getInstance();
	}

	protected List<DatabaseTarget> handleToAll(ShardingInfo shardInfo,
			Object... parameters) {

		List<DatabaseTarget> targets = new LinkedList<DatabaseTarget>();
		String tableNames[] = this.getTables(shardInfo.getTableName());
		for (String shardTableName : tableNames) {
			DefaultDatabaseTarget target = new DefaultDatabaseTarget();
			shardInfo.setTargetTableName(shardTableName);
			target.datasource = this.getDataSource(shardInfo);
			target.parameters = parameters;
			shardInfo.getTable().setName(shardTableName);
			target.sql = shardInfo.generateNewSql();
			target.statementType = shardInfo.getStatementType();
			targets.add(target);
		}
		return targets;
	}

	protected List<DatabaseTarget> handleToSpecifyWithTableName(
			ShardingInfo shardInfo, Object... parameters) {
		shardInfo.setTargetTableName(shardInfo.getTableName());
		shardInfo.getTable().setName(shardInfo.getTableName());
		List<DatabaseTarget> targets = new LinkedList<DatabaseTarget>();
		DefaultDatabaseTarget target = new DefaultDatabaseTarget();
		target.datasource = this.getDataSource(shardInfo);
		target.parameters = parameters;
		target.sql = shardInfo.generateNewSql();
		target.statementType = shardInfo.getStatementType();
		targets.add(target);
		return targets;
	}

	protected List<DatabaseTarget> handleToSpecify(ShardingInfo shardInfo,
			Object... parameters) {
		HashGenerator hashGenerator = getHashGenerator(shardInfo);		
		String toTableName = hashGenerator.generateTableName(createHashInfo(shardInfo));
		shardInfo.setTargetTableName(toTableName);
		shardInfo.getTable().setName(toTableName);
		List<DatabaseTarget> targets = new LinkedList<DatabaseTarget>();
		DefaultDatabaseTarget target = new DefaultDatabaseTarget();
		target.datasource = this.getDataSource(shardInfo);
		target.parameters = parameters;
		target.sql = shardInfo.generateNewSql();
		target.statementType = shardInfo.getStatementType();
		targets.add(target);
		return targets;
	}
	private ShardContext createHashInfo(ShardingInfo shardInfo){
		ShardContext info=new ShardContext();
		info.setTableName(shardInfo.getTableName());
		info.setColumns(shardInfo.getHashColumns());
		info.setValues(shardInfo.getHashColumnsValues());
		return info;
	}

	protected DataSource getDataSource(ShardingInfo shardInfo) {
		return loadBalancer.dispatch(shardInfo);
	}

	protected String[] getTables(String orgTableName) {
		TableConfig config = getConfigurationManager().getTableConfig(
				orgTableName);
		return config.getTables();
	}

}
