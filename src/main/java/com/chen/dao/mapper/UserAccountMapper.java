package com.chen.dao.mapper;

import com.chen.entity.UserAccount;

public interface UserAccountMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table useraccount
     *
     * @mbg.generated
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table useraccount
     *
     * @mbg.generated
     */
    int insert(UserAccount record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table useraccount
     *
     * @mbg.generated
     */
    int insertSelective(UserAccount record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table useraccount
     *
     * @mbg.generated
     */
    UserAccount selectByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table useraccount
     *
     * @mbg.generated
     */
    int updateByPrimaryKeySelective(UserAccount record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table useraccount
     *
     * @mbg.generated
     */
    int updateByPrimaryKey(UserAccount record);

    UserAccount selectUserAccount();
}