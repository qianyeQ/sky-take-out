package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;


    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult list(SetmealPageQueryDTO setmealPageQueryDTO) {
        //配置分页查询的每页展示记录数和页码
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        //套餐查询
        Page<SetmealVO> setmealList = setmealMapper.pageQuery(setmealPageQueryDTO);
        //返回查询数据
        return new PageResult(setmealList.getTotal(),setmealList.getResult());
    }

    /**
     * 新增套餐
     * @param setmealDTO
     * @return
     */
    @Transactional
    @Override
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        //为套餐表插入套餐数据
        setmealMapper.insert(setmeal);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmeal.getId());
        }
        //为套餐关联菜品表插入套餐关联的菜品
        setmealDishMapper.insertByDish(setmealDishes);
    }

    /**
     * 通过id批量删除套餐
     * @param ids
     */
    @Transactional
    @Override
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            SetmealVO setmeal = setmealMapper.getById(id);
            if (setmeal.getStatus().equals(StatusConstant.ENABLE)) {
                throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //先删除套餐和菜品的关联数据，再删除套餐数据
        setmealDishMapper.deleteBySetmealIds(ids);
        setmealMapper.deleteBatch(ids);
    }

    /**
     * 修改套餐以及套餐内的菜品
     * @param setmealDTO
     */
    @Transactional
    @Override
    public void updateWithFlavors(SetmealDTO setmealDTO) {
        //修改套餐
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.update(setmeal);

        //修改套餐内的菜品 先删除套餐内的菜品
        setmealDishMapper.deleteBySetmealId(setmealDTO.getId());
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmealDTO.getId());
        }
        //删除完套餐内的菜品再进行添加
        setmealDishMapper.insertByDish(setmealDishes);
    }

    /**
     * 通过id获取套餐信息
     * @param id
     * @return
     */
    @Override
    public SetmealVO getById(Long id) {
        //获取套餐
        SetmealVO setmealVO = setmealMapper.getById(id);
        //获取套餐关联菜品
        List<SetmealDish> setmealDishesList = setmealDishMapper.getBySetmealId(id);
        //将套餐关联菜品封装到套餐对象中
        setmealVO.setSetmealDishes(setmealDishesList);
        return setmealVO;
    }

    /**
     * 起售停售套餐
     * @param id
     * @param status
     */
    @Override
    public void startOrStop(Long id, Integer status) {
        //获取套餐内的菜品集合并判断菜品是否正在起售状态中
        if(status == StatusConstant.ENABLE) {
            List<SetmealDish> dishList = setmealDishMapper.getBySetmealId(id);
            for (SetmealDish setmealDish : dishList) {
                Dish dish = dishMapper.getById(setmealDish.getDishId());
                if (dish.getStatus() == StatusConstant.DISABLE) {
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }
        }
        //菜品都在起售状态或者套餐本身就在起售状态则可以正常设置套餐状态
        setmealMapper.startOrStop(id,status);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
