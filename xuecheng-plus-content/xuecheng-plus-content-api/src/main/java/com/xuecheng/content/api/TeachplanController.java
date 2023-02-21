package com.xuecheng.content.api;

import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.service.TeachplanService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(value = "课程计划管理相关的接口", tags = "课程计划管理相关的接口")
@RestController
@Slf4j
public class TeachplanController {

    @Autowired
    TeachplanService teachplanService;

    @GetMapping("/teachplan/{courseId}/tree-nodes")
    public List<TeachplanDto> getTreeNodes(@PathVariable("courseId") Long courseId) {
        return teachplanService.findTeachplanTree(courseId);
    }

    @PostMapping("/teachplan")
    public void saveTeachplan(@RequestBody SaveTeachplanDto dto) {
        teachplanService.saveTeachplan(dto);
    }

    @DeleteMapping("/teachplan/{teachplanId}")
    public void deleteTeachplan(@PathVariable("teachplanId") Long teachplanId) {
        teachplanService.deleteTeachplan(teachplanId);
    }

    @PostMapping("/teachplan/moveup/{teachplanId}")
    public void moveUpTeachplan(@PathVariable("teachplanId") Long teachplanId) {
        teachplanService.moveUpTeachplan(teachplanId);
    }

    @PostMapping("/teachplan/movedown/{teachplanId}")
    public void moveDownTeachplan(@PathVariable("teachplanId") Long teachplanId) {
        teachplanService.moveDownTeachplan(teachplanId);
    }

    @ApiOperation(value = "课程计划和媒资信息绑定")
    @PostMapping("/teachplan/association/media")
    void associationMedia(@RequestBody BindTeachplanMediaDto bindTeachplanMediaDto) {
        teachplanService.associationMedia(bindTeachplanMediaDto);
    }

    @ApiOperation(value = "课程计划和媒资信息解除绑定")
    @DeleteMapping("/teachplan/association/media/{teachPlanId}/{mediaId}")
    void delAssociationMedia(@PathVariable Long teachPlanId, @PathVariable Long mediaId) {

    }

}
