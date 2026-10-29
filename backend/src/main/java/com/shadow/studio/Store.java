package com.shadow.studio;

import com.shadow.studio.Models.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface Store {
  @Select("SELECT * FROM templates ORDER BY id")
  List<Template> templates();

  @Select("SELECT * FROM templates WHERE id=#{id}")
  Template template(String id);

  @Insert("INSERT INTO users VALUES(#{id},#{hash})")
  void user(@Param("id") String id, @Param("hash") String hash);

  @Select("SELECT id FROM users WHERE token_hash=#{hash}")
  String identity(String hash);

  @Insert("INSERT INTO assets VALUES(#{id},#{userId},#{mimeType},#{sizeBytes})")
  void asset(Asset asset);

  @Select("SELECT * FROM assets WHERE id=#{id} AND user_id=#{user}")
  Asset assetById(@Param("id") String id, @Param("user") String user);

  @Insert(
      "INSERT INTO tasks(id,user_id,template_id,status,fail_at,delay_ms)"
          + " VALUES(#{id},#{userId},#{templateId},'QUEUED',#{failAt},#{delayMs})")
  void create(Task task);

  @Insert("INSERT INTO task_assets VALUES(#{task},#{asset},#{position})")
  void attach(
      @Param("task") String task, @Param("asset") String asset, @Param("position") int position);

  @Select(
      "SELECT * FROM tasks WHERE user_id=#{user} ORDER BY created_at DESC,id DESC LIMIT #{limit}"
          + " OFFSET #{offset}")
  List<Task> history(
      @Param("user") String user, @Param("limit") int limit, @Param("offset") int offset);

  @Select("SELECT * FROM tasks WHERE id=#{id} AND user_id=#{user}")
  Task task(@Param("id") String id, @Param("user") String user);

  @Select("SELECT * FROM tasks WHERE status='QUEUED' ORDER BY created_at,id LIMIT 1")
  Task next();

  @Update(
      "UPDATE tasks SET status='RUNNING',updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND"
          + " status='QUEUED'")
  int claim(String id);

  @Select("SELECT * FROM tasks WHERE id=#{id} FOR UPDATE")
  Task lockTask(String id);

  @Update(
      "UPDATE tasks SET stage=#{stage},updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND"
          + " status='RUNNING'")
  int stage(@Param("id") String id, @Param("stage") String stage);

  @Insert("INSERT INTO task_steps(task_id,stage,status) VALUES(#{id},#{stage},'RUNNING')")
  void begin(@Param("id") String id, @Param("stage") String stage);

  @Update(
      "UPDATE task_steps SET status=#{status},finished_at=CURRENT_TIMESTAMP(3) WHERE task_id=#{id}"
          + " AND stage=#{stage} AND status='RUNNING'")
  int end(@Param("id") String id, @Param("stage") String stage, @Param("status") String status);

  @Select(
      "SELECT stage,status,started_at,finished_at FROM task_steps WHERE task_id=#{id} ORDER BY"
          + " started_at,FIELD(stage,'ASSET_CHECK','GENERATE','FINALIZE')")
  List<Step> steps(String id);

  @Select(
      "SELECT a.* FROM assets a JOIN task_assets t ON a.id=t.asset_id WHERE t.task_id=#{id} ORDER"
          + " BY position")
  List<Asset> inputs(String id);

  @Update(
      "UPDATE tasks SET status='SUCCEEDED',result_url=#{url},updated_at=CURRENT_TIMESTAMP(3) WHERE"
          + " id=#{id} AND status='RUNNING'")
  int succeed(@Param("id") String id, @Param("url") String url);

  @Update(
      "UPDATE tasks SET"
          + " status='FAILED',failed_stage=#{stage},error=#{error},updated_at=CURRENT_TIMESTAMP(3)"
          + " WHERE id=#{id} AND status='RUNNING'")
  int fail(@Param("id") String id, @Param("stage") String stage, @Param("error") String error);

  @Update(
      "UPDATE tasks SET status='FAILED',failed_stage=stage,error='服务重启中断了制作，请重新创建任务' WHERE"
          + " status='RUNNING'")
  void interrupted();

  @Update(
      "UPDATE task_steps SET status='FAILED',finished_at=CURRENT_TIMESTAMP(3) WHERE"
          + " status='RUNNING'")
  void interruptedSteps();
}
