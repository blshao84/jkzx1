def convert_dict(data):
    if isinstance(data, list):
        new_data = []
        for d in data:
            new_data.append(convert_dict(d))
        return frozenset(new_data)
    elif isinstance(data, dict):
        new_data = []
        for k in data:
            new_data.append((k, convert_dict(data[k])))
        return frozenset(new_data)
    else:
        return data


data = {'a': 1, 'b': [1, 2, 3], 'c': {'aa': 4, 'bb': 5}, 'd': [{'aaa': 6, 'bbb': 7}, {'ccc': 8}]}
data2 = {'a': 1, 'c': {'aa': 4, 'bb': 5}, 'd': [{'aaa': 6, 'bbb': 7}, {'ccc': 8}], 'b': [2, 3, 1], }
x = convert_dict(data)
y = convert_dict(data2)
print(data == data2)
print(x == y)
