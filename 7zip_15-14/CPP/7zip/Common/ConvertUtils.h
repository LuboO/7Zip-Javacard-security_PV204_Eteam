// ConvertUtils.h

#include <vector>
#include <string>

#include "../../Common/MyString.h"

typedef unsigned char BYTE;

/*
 * Class for converting strings and bytes in various encodings.
 */
class ConvertUtils {
public:
	static UString encodeBase64(unsigned char const * bytes , unsigned int len);

	static std::vector<BYTE> ConvertUtils::decodeBase64(std::string const& encoded_string);

	static UString cvrtStrToUni(const std::string & str);

	static std::string cvrtUniToStr(const UString & str);

	static std::vector<BYTE> cvrtUniToByteArr(const UString & str);
};