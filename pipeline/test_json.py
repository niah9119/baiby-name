import json
with open('/work/git/baiby-name/pipeline/data/ssb/raw/ssb-10467-all.json', 'r') as f:
    data = json.load(f)

dim = data.get('dimension', {})
fornavn_idx = dim['Fornavn']['category']['index']
fornavn_label = dim['Fornavn']['category']['label']
tid_idx = dim['Tid']['category']['index']

# Verify 1EMMA, 2024 -> 379
emma_code = '1EMMA'
year = '2024'

emma_idx = forrnavn_idx[emma_code]
year_idx = tid_idx[year]

print('Emma code index:', emma_idx)
print('Year 2024 index:', year_idx)

# Calculate position: name_index * 146 + year_index
size = dim['size']
print('Sizes:', size)

# value[name_index * 146 + year_index]
pos = emma_idx * 146 + year_idx
print('Position for Emma 2024:', pos)
print('Value at that position:', data['value'][pos])

# Check Aage (boy prefix 2)
aage_code = '2AAGE'
aage_idx = forrnavn_idx[aage_code]
pos_aage = aage_idx * 146 + year_idx
print('Aage position:', pos_aage, 'value:', data['value'][pos_aage])

# Verify label for Emma
print('Emma label:', forrnavn_label[emma_code])
print('Aage label:', forrnavn_label[aage_code])
