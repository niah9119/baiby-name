
# BaibyName

## System description

BaibyName is a system that will help a user in selecting a name for his/her baby.
User interface should be a web interface that should work on laptops, surf pads and mobile phones.
Backend should be a database with names categorized in different ways.
The database needs to be populated with all these names. And also categorized.
I think I want a locally running LLM to assist the user when choosing a name.
I'm not yet sure how the selection process should work... 
I think that the user should first try to narrow name scope by answering some questions from LLM and maybe also
select among a bunch of categories.
I also want LLM to state if name works together with the family name, if sounds good when you say the complete name.
I'm planning to use LLM Gemma-4 (google/gemma-4-26B-A4B-it) 

## Categories
### Sex
Boy or Girl
### Country (Multi select) Think we start with Sweden, Norway, Denmark, England and USA.
If selecting Sweden, names common in Sweden should be input to LLM
### Celebrity (has sub sategories)
If a famous person has the name (Ex Leo Messi)
#### Royalty
If a royal person has the name (Ex King Charles)
#### Movie star
If a movie start has the name (Ex Marilyn Monroe)
#### Sports star
If a sports star has the name (Ex Michael Jordan)
### Common lately
If the name has been chosen by many people the last couple of years
### Uncommon lately
If not many people have chosen the name lately



