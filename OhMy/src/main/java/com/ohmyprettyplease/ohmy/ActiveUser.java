package com.ohmyprettyplease.ohmy;

/**
 * Created by Steven on 6/30/13.
 * This class represents the currently logged in user
 */
public class ActiveUser
{
    private String mName;
    private String mEmail;
    private String mPassword;

    public ActiveUser (String name, String email, String password)
    {
        mName = name;
        mEmail = email;
        mPassword = password;
    }

    public String getName ()
    {return mName;}

    public String getEmail()
    {return mEmail;}

    public String getPassword()
    {return mPassword;}

    public void setName(String name)
    {mName = name;}

    public void setEmail(String email)
    {mEmail = email;}

    public void setPassword (String password)
    {mPassword = password;}

}